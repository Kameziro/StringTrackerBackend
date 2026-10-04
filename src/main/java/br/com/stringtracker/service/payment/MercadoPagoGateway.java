package br.com.stringtracker.service.payment;

import br.com.stringtracker.client.MercadoPagoClient;
import br.com.stringtracker.client.MercadoPagoClient.CreateOrderRequest;
import br.com.stringtracker.client.MercadoPagoClient.Order;
import br.com.stringtracker.client.MercadoPagoClient.OrderPaymentMethod;
import br.com.stringtracker.client.MercadoPagoClient.Payer;
import br.com.stringtracker.client.MercadoPagoClient.PaymentMethodRequest;
import br.com.stringtracker.client.MercadoPagoClient.PaymentRequest;
import br.com.stringtracker.client.MercadoPagoClient.Transactions;
import br.com.stringtracker.client.MercadoPagoOAuthClient;
import br.com.stringtracker.client.MercadoPagoOAuthClient.TokenResponse;
import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubPaymentStatus;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.service.BusinessRuleException;
import br.com.stringtracker.service.PaymentProviderException;
import br.com.stringtracker.service.PaymentProviderUnavailableException;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * Pix pela Orders API do Mercado Pago, em nome do clube (spike T1). Valores trafegam como string de reais
 * convertida dos centavos; nenhum campo de comissão é enviado.
 */
@ApplicationScoped
public class MercadoPagoGateway implements PaymentGateway {

    private static final Duration TOKEN_RENEWAL_MARGIN = Duration.ofMinutes(5);
    private static final String NOT_CONNECTED_MESSAGE = "O clube não tem conta de recebimento conectada";

    @Inject
    @RestClient
    MercadoPagoClient client;

    @Inject
    @RestClient
    MercadoPagoOAuthClient oauthClient;

    @Inject
    ClubRepository clubRepository;

    @Inject
    TokenCipher tokenCipher;

    @Inject
    Clock clock;

    @ConfigProperty(name = "mp.client-id")
    String clientId;

    @ConfigProperty(name = "mp.client-secret")
    String clientSecret;

    @Override
    public PixCharge createPix(ClubCredentials credentials, long amountCents, String description, String payerEmail,
                               String externalReference, Duration expiresIn, String idempotencyKey) {
        String amount = reais(amountCents);
        CreateOrderRequest request = new CreateOrderRequest("online", externalReference, "automatic", amount,
                description, new Payer(payerEmail),
                new Transactions(List.of(new PaymentRequest(amount, new PaymentMethodRequest("pix", "bank_transfer"),
                        expiresIn.toString()))));
        Order order = call("Não foi possível gerar o Pix. Tente novamente",
                () -> client.createOrder(bearer(credentials), idempotencyKey, request));
        OrderPaymentMethod pix = pixOf(order);
        if (pix == null || pix.qrCode() == null) {
            throw new PaymentProviderException("O Mercado Pago não devolveu o QR Code do Pix", null);
        }
        return new PixCharge(order.id(), pix.qrCode(), pix.qrCodeBase64(), pix.ticketUrl());
    }

    @Override
    public ProviderPayment getOrder(ClubCredentials credentials, String providerOrderId) {
        Order order = call("Não foi possível consultar o pagamento. Tente novamente",
                () -> client.getOrder(bearer(credentials), providerOrderId));
        return new ProviderPayment(order.id(), toStatus(order));
    }

    @Override
    public void refund(ClubCredentials credentials, String providerOrderId, String idempotencyKey) {
        call("Não foi possível reembolsar o pagamento. Tente novamente",
                () -> client.refund(bearer(credentials), idempotencyKey, providerOrderId));
    }

    @Override
    public void cancel(ClubCredentials credentials, String providerOrderId, String idempotencyKey) {
        call("Não foi possível cancelar o Pix. Tente novamente",
                () -> client.cancel(bearer(credentials), idempotencyKey, providerOrderId));
    }

    @Override
    public ClubCredentials refreshIfNeeded(Club club) {
        if (club.getPaymentStatus() != ClubPaymentStatus.CONNECTED || club.getMpAccessTokenEnc() == null) {
            throw new BusinessRuleException(NOT_CONNECTED_MESSAGE);
        }
        if (!expiresSoon(club)) {
            return new ClubCredentials(tokenCipher.decrypt(club.getMpAccessTokenEnc()));
        }
        // Renovação em transação própria: ela confirma (tokens novos ou clube desconectado) mesmo que o
        // chamador desfaça a dele, e o lock da linha serializa renovações simultâneas (o refresh token vale uma vez).
        String accessToken = QuarkusTransaction.requiringNew().call(() -> renew(club.getId()));
        if (accessToken == null) {
            throw new PaymentProviderException("A conta de recebimento do clube foi desconectada. Reconecte-a", null);
        }
        return new ClubCredentials(accessToken);
    }

    /** Token vigente do clube, renovado se preciso; nulo se o provedor recusou a renovação e o clube foi desconectado. */
    private String renew(long clubId) {
        Club club = clubRepository.findById(clubId, LockModeType.PESSIMISTIC_WRITE);
        if (club.getPaymentStatus() != ClubPaymentStatus.CONNECTED) {
            throw new BusinessRuleException(NOT_CONNECTED_MESSAGE);
        }
        if (!expiresSoon(club)) {
            return tokenCipher.decrypt(club.getMpAccessTokenEnc());
        }
        TokenResponse tokens;
        try {
            tokens = oauthClient.refreshToken("refresh_token", clientId, clientSecret,
                    tokenCipher.decrypt(club.getMpRefreshTokenEnc()));
        } catch (WebApplicationException | ProcessingException e) {
            if (isUnavailable(e)) {
                throw new PaymentProviderUnavailableException("O Mercado Pago não respondeu. Tente novamente", e);
            }
            club.setPaymentStatus(ClubPaymentStatus.NOT_CONNECTED);
            return null;
        }
        club.setMpAccessTokenEnc(tokenCipher.encrypt(tokens.accessToken()));
        club.setMpRefreshTokenEnc(tokenCipher.encrypt(tokens.refreshToken()));
        club.setMpTokenExpiresAt(clock.instant().plusSeconds(tokens.expiresIn()));
        return tokens.accessToken();
    }

    private boolean expiresSoon(Club club) {
        Instant expiresAt = club.getMpTokenExpiresAt();
        return expiresAt == null || expiresAt.isBefore(clock.instant().plus(TOKEN_RENEWAL_MARGIN));
    }

    private static String bearer(ClubCredentials credentials) {
        return "Bearer " + credentials.accessToken();
    }

    private static String reais(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }

    private static OrderPaymentMethod pixOf(Order order) {
        if (order.transactions() == null || order.transactions().payments() == null
                || order.transactions().payments().isEmpty()) {
            return null;
        }
        return order.transactions().payments().get(0).paymentMethod();
    }

    // Tabela do spike T1. `action_required` é Pix ainda não pago; `processed` só vale como aprovado se acreditado.
    private static PaymentStatus toStatus(Order order) {
        String status = order.status() == null ? "" : order.status();
        return switch (status) {
            case "action_required" -> PaymentStatus.PENDING;
            case "processed" -> {
                if ("accredited".equals(order.statusDetail())) {
                    yield PaymentStatus.APPROVED;
                }
                throw unknownState(order);
            }
            case "canceled" -> PaymentStatus.EXPIRED;
            case "refunded" -> PaymentStatus.REFUNDED;
            default -> throw unknownState(order);
        };
    }

    private static PaymentProviderException unknownState(Order order) {
        return new PaymentProviderException(
                "Estado de pedido desconhecido no Mercado Pago: %s/%s".formatted(order.status(), order.statusDetail()),
                null);
    }

    /** Chama o provedor: timeout, falha de rede e 5xx/408/429 viram "indisponível" (503); outros erros, recusa (502). */
    private static <T> T call(String unavailableMessage, Supplier<T> request) {
        try {
            return request.get();
        } catch (WebApplicationException | ProcessingException e) {
            if (isUnavailable(e)) {
                throw new PaymentProviderUnavailableException(unavailableMessage, e);
            }
            throw new PaymentProviderException("O Mercado Pago recusou a operação", e);
        }
    }

    private static boolean isUnavailable(RuntimeException error) {
        if (error instanceof WebApplicationException web) {
            int status = web.getResponse().getStatus();
            return status >= 500 || status == 408 || status == 429;
        }
        return true;
    }
}
