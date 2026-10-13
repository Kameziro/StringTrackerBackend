package br.com.stringtracker.service.payment;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.schedule.PaymentStatus;

import java.time.Duration;

/**
 * Provedor de pagamento Pix atrás de uma interface pequena (AD-012; o plano B é o Asaas). Todas as chamadas
 * valem na conta do clube, com o token OAuth dele, e falham com
 * {@link br.com.stringtracker.service.PaymentProviderUnavailableException} quando o provedor não responde
 * ou com {@link br.com.stringtracker.service.PaymentProviderException} quando ele recusa.
 */
public interface PaymentGateway {

    /** Token da conta do clube, já renovado se estava perto de vencer. */
    record ClubCredentials(String accessToken) {
    }

    /** Cobrança Pix criada; {@code providerOrderId} é o id do pedido no provedor. */
    record PixCharge(String providerOrderId, String qrCode, String qrCodeBase64, String ticketUrl) {
    }

    /** Estado do pedido no provedor, já traduzido para o estado interno do pagamento. */
    record ProviderPayment(String providerOrderId, PaymentStatus status) {
    }

    // SPEC_DEVIATION: o design previa createPix(c, amountCents, description, expiresAt, key).
    // Reason: a Orders API exige o e-mail do pagador e a referência externa, e expiration_time é uma duração (PT10M).
    PixCharge createPix(ClubCredentials credentials, long amountCents, String description, String payerEmail,
                        String externalReference, Duration expiresIn, String idempotencyKey);

    ProviderPayment getOrder(ClubCredentials credentials, String providerOrderId);

    /** Reembolso total do pedido. Repetir com a mesma chave não cria um segundo reembolso. */
    void refund(ClubCredentials credentials, String providerOrderId, String idempotencyKey);

    /** Invalida o Pix ainda não pago. */
    void cancel(ClubCredentials credentials, String providerOrderId, String idempotencyKey);

    /** Credenciais do clube, renovando o token OAuth antes de ele vencer; 422 se o clube não tem conta conectada. */
    ClubCredentials refreshIfNeeded(Club club);
}
