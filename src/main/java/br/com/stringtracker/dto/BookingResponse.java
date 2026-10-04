package br.com.stringtracker.dto;

import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Reserva de aula. {@code pix} só vem na criação (o QR Code não é guardado); o app acompanha
 * a reserva por {@code status} e conta o prazo a partir de {@code holdExpiresAt}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingResponse(
        long bookingId,
        BookingStatus status,
        LessonType lessonType,
        long priceCents,
        String partnerName,
        Instant startsAt,
        Instant endsAt,
        Instant holdExpiresAt,
        Pix pix
) {

    public record Pix(String qrCodeBase64, String copiaECola, String ticketUrl, Instant expiresAt) {
    }

    public static BookingResponse from(Booking booking, Pix pix) {
        return new BookingResponse(booking.getId(), booking.getStatus(), booking.getLessonType(),
                booking.getPriceCents(), booking.getPartnerName(), booking.getLessonSlot().getStartsAt(),
                booking.getLessonSlot().getEndsAt(), booking.getHoldExpiresAt(), pix);
    }
}
