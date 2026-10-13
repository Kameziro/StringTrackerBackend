package br.com.stringtracker.dto;

import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonType;
import br.com.stringtracker.model.schedule.PaymentMode;
import br.com.stringtracker.model.schedule.RefundStatus;
import br.com.stringtracker.service.BookingCancellationService;
import br.com.stringtracker.service.MinioObjectStorage;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Aulas do aluno para a aba Agenda. {@code upcoming} são as confirmadas que ainda não terminaram, da mais próxima
 * para a mais distante; {@code past} são as que já terminaram e as canceladas, da mais recente para a mais antiga.
 */
public record MyLessonsResponse(List<Lesson> upcoming, List<Lesson> past) {

    /**
     * Uma aula do aluno. {@code fullRefundUntil} é o último instante em que cancelar devolve o valor integral (só nas
     * aulas confirmadas); depois dele o cancelamento não reembolsa. Nas canceladas, {@code cancelledAt},
     * {@code refundStatus} e {@code refundAmountCents} dizem o que foi devolvido.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Lesson(
            long bookingId,
            BookingStatus status,
            LessonType lessonType,
            long priceCents,
            PaymentMode paymentMode,
            String partnerName,
            Instant startsAt,
            Instant endsAt,
            long clubId,
            String clubName,
            String clubAddress,
            long coachId,
            String coachName,
            String coachAvatarUrl,
            Instant fullRefundUntil,
            Instant cancelledAt,
            RefundStatus refundStatus,
            Long refundAmountCents
    ) {

        public static Lesson from(Booking booking, MinioObjectStorage storage) {
            LessonSlot slot = booking.getLessonSlot();
            var club = slot.getClubCoach().getClub();
            var coachUser = slot.getCoach().getUser();
            Instant fullRefundUntil = booking.getStatus() == BookingStatus.CONFIRMED
                    ? slot.getStartsAt().minus(BookingCancellationService.FULL_REFUND_NOTICE) : null;
            return new Lesson(booking.getId(), booking.getStatus(), booking.getLessonType(), booking.getPriceCents(),
                    booking.getPaymentMode(), booking.getPartnerName(), slot.getStartsAt(), slot.getEndsAt(),
                    club.getId(), club.getName(), club.getAddress(), slot.getCoach().getId(), coachUser.getName(),
                    storage.toClientMediaUrl(coachUser.getAvatarUrl()), fullRefundUntil, booking.getCancelledAt(),
                    booking.getRefundStatus(), booking.getRefundAmountCents());
        }
    }
}
