package br.com.stringtracker.dto;

import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonType;
import br.com.stringtracker.model.schedule.Payment;
import br.com.stringtracker.model.schedule.PaymentMode;
import br.com.stringtracker.model.schedule.RefundStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Reserva de aula. {@code pix} vem enquanto a reserva está HELD (na criação e em cada leitura, para o app reabrir a
 * tela de pagamento) e nunca depois; o app acompanha a reserva por {@code status} e conta o prazo a partir de
 * {@code holdExpiresAt}. Depois do cancelamento,
 * {@code cancelledAt}, {@code refundStatus} e {@code refundAmountCents} dizem se e quanto foi devolvido.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingResponse(
        long bookingId,
        BookingStatus status,
        LessonType lessonType,
        long priceCents,
        PaymentMode paymentMode,
        String partnerName,
        Instant startsAt,
        Instant endsAt,
        Instant holdExpiresAt,
        Instant cancelledAt,
        RefundStatus refundStatus,
        Long refundAmountCents,
        Pix pix
) {

    public record Pix(String qrCodeBase64, String copiaECola, String ticketUrl, Instant expiresAt) {

        /** Os dados guardados na cobrança; nulo se ela não os tem (cobranças anteriores à V15). */
        public static Pix of(Payment payment) {
            if (payment.getPixCopiaECola() == null) {
                return null;
            }
            return new Pix(payment.getPixQrCodeBase64(), payment.getPixCopiaECola(), payment.getPixTicketUrl(),
                    payment.getExpiresAt());
        }
    }

    public static BookingResponse from(Booking booking, Pix pix) {
        return new BookingResponse(booking.getId(), booking.getStatus(), booking.getLessonType(),
                booking.getPriceCents(), booking.getPaymentMode(), booking.getPartnerName(), booking.getLessonSlot().getStartsAt(),
                booking.getLessonSlot().getEndsAt(), booking.getHoldExpiresAt(), booking.getCancelledAt(),
                booking.getRefundStatus(), booking.getRefundAmountCents(), pix);
    }
}
