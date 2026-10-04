package br.com.stringtracker.service.payment;

import br.com.stringtracker.client.MercadoPagoClient;
import br.com.stringtracker.client.MercadoPagoClient.CreateOrderRequest;
import br.com.stringtracker.client.MercadoPagoClient.Order;
import br.com.stringtracker.client.MercadoPagoClient.OrderPayment;
import br.com.stringtracker.client.MercadoPagoClient.OrderPaymentMethod;
import br.com.stringtracker.client.MercadoPagoClient.OrderTransactions;
import br.com.stringtracker.client.MercadoPagoOAuthClient;
import br.com.stringtracker.client.MercadoPagoOAuthClient.TokenResponse;
import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubPaymentStatus;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.service.BusinessRuleException;
import br.com.stringtracker.service.ClockProducer;
import br.com.stringtracker.service.PaymentProviderException;
import br.com.stringtracker.service.PaymentProviderUnavailableException;
import br.com.stringtracker.service.payment.PaymentGateway.ClubCredentials;
import br.com.stringtracker.service.payment.PaymentGateway.PixCharge;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
class MercadoPagoGatewayTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final ClubCredentials CREDENTIALS = new ClubCredentials("club-token");

    @InjectMock
    @RestClient
    MercadoPagoClient client;

    @InjectMock
    @RestClient
    MercadoPagoOAuthClient oauthClient;

    @Inject
    MercadoPagoGateway gateway;

    @Inject
    ClubRepository clubRepository;

    @Inject
    TokenCipher cipher;

    @Inject
    ObjectMapper json;

    @BeforeEach
    void freezeClock() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
    }

    private static Order pixOrder(String id, String status, String detail) {
        return new Order(id, status, detail, new OrderTransactions(List.of(new OrderPayment("PAY1", status,
                new OrderPaymentMethod("00020126-copia-e-cola", "base64-qr", "https://mp.example/ticket")))));
    }

    private PixCharge createPix(long amountCents) {
        return gateway.createPix(CREDENTIALS, amountCents, "Aula de padel", "aluno@example.com", "booking-7",
                Duration.ofMinutes(10), "booking-7");
    }

    private static WebApplicationException http(int status) {
        return new WebApplicationException(Response.status(status).build());
    }

    @Test
    void createPix_sendsTheOrderInReaisWithPixTenMinuteExpiryTokenAndIdempotencyKey() throws Exception {
        when(client.createOrder(anyString(), anyString(), any())).thenReturn(pixOrder("ORD1", "action_required",
                "waiting_transfer"));

        createPix(9000);

        ArgumentCaptor<CreateOrderRequest> request = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(client).createOrder(eq("Bearer club-token"), eq("booking-7"), request.capture());
        JsonNode body = json.readTree(json.writeValueAsString(request.getValue()));
        assertEquals("online", body.get("type").asText());
        assertEquals("automatic", body.get("processing_mode").asText());
        assertEquals("booking-7", body.get("external_reference").asText());
        assertEquals("90.00", body.get("total_amount").asText());
        assertEquals("aluno@example.com", body.get("payer").get("email").asText());
        JsonNode payment = body.get("transactions").get("payments").get(0);
        assertEquals("90.00", payment.get("amount").asText());
        assertEquals("PT10M", payment.get("expiration_time").asText());
        assertEquals("pix", payment.get("payment_method").get("id").asText());
        assertEquals("bank_transfer", payment.get("payment_method").get("type").asText());
        // A plataforma não retém nada: nenhum campo de comissão vai ao provedor.
        assertFalse(body.toString().contains("fee"));
        assertFalse(body.toString().contains("commission"));
    }

    @ParameterizedTest
    @CsvSource({"5,0.05", "100,1.00", "12345,123.45", "9000,90.00"})
    void createPix_convertsCentsToAStringOfReais(long cents, String reais) {
        when(client.createOrder(anyString(), anyString(), any())).thenReturn(pixOrder("ORD1", "action_required",
                "waiting_transfer"));

        createPix(cents);

        ArgumentCaptor<CreateOrderRequest> request = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(client).createOrder(anyString(), anyString(), request.capture());
        assertEquals(reais, request.getValue().totalAmount());
        assertEquals(reais, request.getValue().transactions().payments().get(0).amount());
    }

    @Test
    void createPix_returnsTheOrderIdAndTheQrCodes() {
        when(client.createOrder(anyString(), anyString(), any())).thenReturn(pixOrder("ORD1", "action_required",
                "waiting_transfer"));

        PixCharge charge = createPix(9000);

        assertEquals("ORD1", charge.providerOrderId());
        assertEquals("00020126-copia-e-cola", charge.qrCode());
        assertEquals("base64-qr", charge.qrCodeBase64());
        assertEquals("https://mp.example/ticket", charge.ticketUrl());
    }

    @Test
    void createPix_withoutQrCodeInTheResponse_isAProviderError() {
        when(client.createOrder(anyString(), anyString(), any()))
                .thenReturn(new Order("ORD1", "action_required", "waiting_transfer", null));

        assertThrows(PaymentProviderException.class, () -> createPix(9000));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "action_required,waiting_transfer,PENDING",
            "processed,accredited,APPROVED",
            "canceled,expired,EXPIRED",
            "canceled,,EXPIRED",
            "refunded,refunded,REFUNDED"
    })
    void getOrder_mapsTheProviderStateToTheInternalPaymentStatus(String status, String detail, PaymentStatus expected) {
        when(client.getOrder("Bearer club-token", "ORD1")).thenReturn(new Order("ORD1", status, detail, null));

        assertEquals(expected, gateway.getOrder(CREDENTIALS, "ORD1").status());
    }

    @ParameterizedTest
    @CsvSource(value = {"processed,partially_refunded", "failed,rejected", "mystery,"})
    void getOrder_withAnUnmappedState_isAProviderError(String status, String detail) {
        when(client.getOrder(anyString(), anyString())).thenReturn(new Order("ORD1", status, detail, null));

        assertThrows(PaymentProviderException.class, () -> gateway.getOrder(CREDENTIALS, "ORD1"));
    }

    @Test
    void refundAndCancel_callTheOrderWithTheirOwnIdempotencyKeys() {
        gateway.refund(CREDENTIALS, "ORD1", "refund-7");
        gateway.cancel(CREDENTIALS, "ORD1", "cancel-7");

        verify(client).refund("Bearer club-token", "refund-7", "ORD1");
        verify(client).cancel("Bearer club-token", "cancel-7", "ORD1");
    }

    @Test
    void timeoutsAndServerErrors_becomeProviderUnavailable() {
        when(client.createOrder(anyString(), anyString(), any()))
                .thenThrow(new ProcessingException("Read timed out"))
                .thenThrow(http(500))
                .thenThrow(http(503))
                .thenThrow(http(429));

        for (int attempt = 0; attempt < 4; attempt++) {
            PaymentProviderUnavailableException error =
                    assertThrows(PaymentProviderUnavailableException.class, () -> createPix(9000));
            assertEquals("Não foi possível gerar o Pix. Tente novamente", error.getMessage());
        }
        when(client.getOrder(anyString(), anyString())).thenThrow(http(502));
        when(client.refund(anyString(), anyString(), anyString())).thenThrow(new ProcessingException("reset"));
        when(client.cancel(anyString(), anyString(), anyString())).thenThrow(http(504));
        assertThrows(PaymentProviderUnavailableException.class, () -> gateway.getOrder(CREDENTIALS, "ORD1"));
        assertThrows(PaymentProviderUnavailableException.class, () -> gateway.refund(CREDENTIALS, "ORD1", "k"));
        assertThrows(PaymentProviderUnavailableException.class, () -> gateway.cancel(CREDENTIALS, "ORD1", "k"));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 422})
    void providerRefusals_becomeProviderErrorsNotUnavailable(int status) {
        when(client.createOrder(anyString(), anyString(), any())).thenThrow(http(status));

        // As duas exceções são independentes: só uma recusa (502) satisfaz esta asserção, nunca "indisponível" (503).
        assertThrows(PaymentProviderException.class, () -> createPix(9000));
    }

    // --- renovação do token OAuth ---

    private long connectedClub(Instant tokenExpiresAt) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Club club = Club.create("Gateway Clube " + UUID.randomUUID());
            club.setPaymentStatus(ClubPaymentStatus.CONNECTED);
            club.setMpUserId("123");
            club.setMpAccessTokenEnc(cipher.encrypt("access-old"));
            club.setMpRefreshTokenEnc(cipher.encrypt("refresh-old"));
            club.setMpTokenExpiresAt(tokenExpiresAt);
            clubRepository.persist(club);
            return club.getId();
        });
    }

    private Club load(long clubId) {
        return QuarkusTransaction.requiringNew().call(() -> clubRepository.findById(clubId));
    }

    @Test
    void refreshIfNeeded_withAValidToken_returnsItWithoutCallingTheProvider() {
        Club club = load(connectedClub(NOW.plus(Duration.ofHours(1))));

        assertEquals("access-old", gateway.refreshIfNeeded(club).accessToken());

        verifyNoInteractions(oauthClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-05T11:00:00Z", "2026-10-05T12:03:00Z"})
    void refreshIfNeeded_withAnExpiredOrAlmostExpiredToken_renewsAndStoresTheNewTokensEncrypted(String expiresAt) {
        long clubId = connectedClub(Instant.parse(expiresAt));
        when(oauthClient.refreshToken("refresh_token", "test-client-id", "test-client-secret", "refresh-old"))
                .thenReturn(new TokenResponse("access-new", "refresh-new", 15_552_000L, 123L));

        ClubCredentials renewed = gateway.refreshIfNeeded(load(clubId));

        assertEquals("access-new", renewed.accessToken());
        Club stored = load(clubId);
        assertEquals("access-new", cipher.decrypt(stored.getMpAccessTokenEnc()));
        assertEquals("refresh-new", cipher.decrypt(stored.getMpRefreshTokenEnc()));
        assertEquals(NOW.plusSeconds(15_552_000L), stored.getMpTokenExpiresAt());
        assertEquals(ClubPaymentStatus.CONNECTED, stored.getPaymentStatus());
    }

    @Test
    void refreshIfNeeded_whenTheProviderRefusesTheRefreshToken_disconnectsTheClub() {
        long clubId = connectedClub(NOW.minus(Duration.ofHours(1)));
        when(oauthClient.refreshToken(anyString(), anyString(), anyString(), anyString())).thenThrow(http(400));

        assertThrows(PaymentProviderException.class, () -> gateway.refreshIfNeeded(load(clubId)));

        assertEquals(ClubPaymentStatus.NOT_CONNECTED, load(clubId).getPaymentStatus());
        BusinessRuleException afterwards =
                assertThrows(BusinessRuleException.class, () -> gateway.refreshIfNeeded(load(clubId)));
        assertEquals("O clube não tem conta de recebimento conectada", afterwards.getMessage());
    }

    @Test
    void refreshIfNeeded_whenTheProviderIsDown_keepsTheClubConnected() {
        long clubId = connectedClub(NOW.minus(Duration.ofHours(1)));
        when(oauthClient.refreshToken(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new ProcessingException("timeout"));

        assertThrows(PaymentProviderUnavailableException.class, () -> gateway.refreshIfNeeded(load(clubId)));

        Club stored = load(clubId);
        assertEquals(ClubPaymentStatus.CONNECTED, stored.getPaymentStatus());
        assertEquals("access-old", cipher.decrypt(stored.getMpAccessTokenEnc()));
    }

    @Test
    void refreshIfNeeded_forAClubWithoutAConnectedAccount_isRefusedWithoutCallingTheProvider() {
        Club club = QuarkusTransaction.requiringNew().call(() -> {
            Club created = Club.create("Gateway Sem Conta " + UUID.randomUUID());
            clubRepository.persist(created);
            return created;
        });

        assertThrows(BusinessRuleException.class, () -> gateway.refreshIfNeeded(club));

        verifyNoInteractions(oauthClient);
        verify(client, never()).createOrder(anyString(), anyString(), any());
    }
}
