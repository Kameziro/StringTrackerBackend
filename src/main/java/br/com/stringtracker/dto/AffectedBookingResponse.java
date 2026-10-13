package br.com.stringtracker.dto;

import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonType;
import br.com.stringtracker.model.schedule.RefundStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Reserva atingida por um bloqueio de dia ou pela saída de um professor: na pré-visualização ela ainda está
 * ativa; depois da confirmação vem cancelada, com {@code refundStatus} e {@code refundAmountCents}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AffectedBookingResponse(
        long bookingId,
        long slotId,
        long clubId,
        String clubName,
        Instant startsAt,
        LessonType lessonType,
        BookingStatus status,
        String studentName,
        String studentPhone,
        RefundStatus refundStatus,
        Long refundAmountCents
) {

    public static AffectedBookingResponse from(Booking booking) {
        LessonSlot slot = booking.getLessonSlot();
        return new AffectedBookingResponse(booking.getId(), slot.getId(), slot.getClubCoach().getClub().getId(),
                slot.getClubCoach().getClub().getName(), slot.getStartsAt(), booking.getLessonType(),
                booking.getStatus(), booking.studentName(), booking.getGuestPhone(), booking.getRefundStatus(),
                booking.getRefundAmountCents());
    }
}
