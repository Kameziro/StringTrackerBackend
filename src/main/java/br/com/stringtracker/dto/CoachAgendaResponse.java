package br.com.stringtracker.dto;

import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonKind;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonType;
import br.com.stringtracker.model.schedule.PaymentMode;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.model.schedule.SlotAvailability;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Agenda do professor entre {@code from} e {@code to} (datas inclusive): os horários de todos os clubes dele, com o
 * nome do clube, a situação do horário e as reservas ativas. Horário sem reserva vem com {@code lessons} vazio.
 */
public record CoachAgendaResponse(LocalDate from, LocalDate to, List<Slot> slots) {

    public record Slot(
            long slotId,
            long clubId,
            String clubName,
            Instant startsAt,
            Instant endsAt,
            LessonKind kind,
            int capacity,
            SlotAvailability status,
            List<Lesson> lessons
    ) {

        public static Slot from(LessonSlot slot, SlotAvailability status, List<Lesson> lessons) {
            return new Slot(slot.getId(), slot.getClubCoach().getClub().getId(),
                    slot.getClubCoach().getClub().getName(), slot.getStartsAt(), slot.getEndsAt(), slot.getKind(),
                    slot.getCapacity(), status, lessons);
        }
    }

    /**
     * Reserva ativa de um horário. Aluno com conta vem com {@code studentUserId}; sem conta, com {@code studentPhone}.
     * {@code paymentStatus} só existe para reserva paga com Pix; reserva por fora vem com {@code paymentMode = OFFLINE}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Lesson(
            long bookingId,
            BookingStatus status,
            LessonType lessonType,
            String studentName,
            Long studentUserId,
            String studentPhone,
            String partnerName,
            PaymentMode paymentMode,
            PaymentStatus paymentStatus
    ) {

        public static Lesson from(Booking booking, PaymentStatus paymentStatus) {
            var student = booking.getStudentUser();
            return new Lesson(booking.getId(), booking.getStatus(), booking.getLessonType(), booking.studentName(),
                    student == null ? null : student.getId(), booking.getGuestPhone(), booking.getPartnerName(),
                    booking.getPaymentMode(), paymentStatus);
        }
    }
}
