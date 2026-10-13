package br.com.stringtracker.dto;

import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonType;
import br.com.stringtracker.model.schedule.PaymentMode;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.model.schedule.RefundStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Reservas do clube cujo horário começa entre {@code from} e {@code to} (datas inclusivas), de qualquer estado,
 * por início do horário. Serve ao painel para conferir pagamentos e reembolsos.
 */
public record ClubBookingsResponse(LocalDate from, LocalDate to, List<Item> bookings) {

    /**
     * {@code paymentStatus} só existe em reserva paga com Pix. {@code student.userId} e {@code student.emailHint}
     * existem para aluno com conta, {@code student.phone} para aluno sem conta. {@code refundResolvedAt} e
     * {@code refundResolvedBy} (nome do admin) só existem num reembolso {@code RESOLVED_MANUALLY}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(
            long bookingId,
            long slotId,
            Instant startsAt,
            Instant endsAt,
            long coachId,
            String coachName,
            LessonType lessonType,
            BookingStatus status,
            long priceCents,
            PaymentMode paymentMode,
            PaymentStatus paymentStatus,
            String partnerName,
            Student student,
            Instant cancelledAt,
            RefundStatus refundStatus,
            Long refundAmountCents,
            Instant refundResolvedAt,
            String refundResolvedBy
    ) {

        public static Item from(Booking booking, PaymentStatus paymentStatus) {
            var slot = booking.getLessonSlot();
            var resolvedBy = booking.getRefundResolvedBy();
            return new Item(booking.getId(), slot.getId(), slot.getStartsAt(), slot.getEndsAt(),
                    slot.getCoach().getId(), slot.getCoach().getUser().getName(), booking.getLessonType(),
                    booking.getStatus(), booking.getPriceCents(), booking.getPaymentMode(), paymentStatus,
                    booking.getPartnerName(), Student.from(booking), booking.getCancelledAt(),
                    booking.getRefundStatus(), booking.getRefundAmountCents(), booking.getRefundResolvedAt(),
                    resolvedBy == null ? null : resolvedBy.getName());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Student(Long userId, String name, String emailHint, String phone) {

        static Student from(Booking booking) {
            var user = booking.getStudentUser();
            return user == null
                    ? new Student(null, booking.getGuestName(), null, booking.getGuestPhone())
                    : new Student(user.getId(), user.getName(), StudentSummaryResponse.maskEmail(user.getEmail()), null);
        }
    }
}
