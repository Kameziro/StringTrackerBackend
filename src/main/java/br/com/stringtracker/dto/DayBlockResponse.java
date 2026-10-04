package br.com.stringtracker.dto;

import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonType;
import br.com.stringtracker.model.schedule.RefundStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Resultado do bloqueio de um dia. {@code applied = false} é a pré-visualização: nada mudou e {@code affected}
 * lista as reservas que o bloqueio cancelaria. Com {@code applied = true}, {@code affected} traz as reservas
 * canceladas, com o reembolso de cada uma.
 */
public record DayBlockResponse(LocalDate date, boolean applied, List<Affected> affected) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Affected(
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

        public static Affected from(Booking booking) {
            LessonSlot slot = booking.getLessonSlot();
            return new Affected(booking.getId(), slot.getId(), slot.getClubCoach().getClub().getId(),
                    slot.getClubCoach().getClub().getName(), slot.getStartsAt(), booking.getLessonType(),
                    booking.getStatus(), booking.studentName(), booking.getGuestPhone(), booking.getRefundStatus(),
                    booking.getRefundAmountCents());
        }
    }
}
