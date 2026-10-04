package br.com.stringtracker.model.schedule;

import br.com.stringtracker.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Cobrança Pix de uma reserva ({@code payment_mode = PIX}), criada no provedor em nome do clube. */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false, length = 16)
    private String provider;

    @Column(name = "provider_payment_id", length = 64)
    private String providerPaymentId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "refund_attempts", nullable = false)
    private int refundAttempts;

    @Column(name = "next_refund_at")
    private Instant nextRefundAt;

    /** Código Pix copia e cola, guardado para reabrir a tela de pagamento; nulo nas cobranças anteriores à V15. */
    @Column(name = "pix_copia_e_cola", columnDefinition = "text")
    private String pixCopiaECola;

    @Column(name = "pix_qr_code_base64", columnDefinition = "text")
    private String pixQrCodeBase64;

    @Column(name = "pix_ticket_url", columnDefinition = "text")
    private String pixTicketUrl;

    public static Payment create(Booking booking, String provider, long amountCents, Instant expiresAt) {
        Payment payment = new Payment();
        payment.booking = booking;
        payment.provider = provider;
        payment.amountCents = amountCents;
        payment.expiresAt = expiresAt;
        return payment;
    }
}
