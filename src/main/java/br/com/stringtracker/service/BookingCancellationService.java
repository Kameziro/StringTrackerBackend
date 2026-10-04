package br.com.stringtracker.service;

import br.com.stringtracker.dto.BookingResponse;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.User;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.service.payment.RefundService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Cancelamento de reserva por aluno, clube ou professor, com o registro de quem cancelou, quando e quanto foi
 * devolvido (CANC-06). O aluno só tem reembolso com 24h ou mais de antecedência; clube e professor devolvem
 * sempre o valor integral. Todo cancelamento acontece com a linha da reserva travada, antes do pagamento.
 */
@ApplicationScoped
public class BookingCancellationService {

    /** Antecedência mínima para o aluno ser reembolsado quando cancela (CANC-01, CANC-02). */
    public static final Duration FULL_REFUND_NOTICE = Duration.ofHours(24);

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ClubAccessService access;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    BookingService bookingService;

    @Inject
    RefundService refundService;

    @Inject
    Clock clock;

    @Transactional
    public BookingResponse cancelByStudent(long bookingId) {
        User student = currentUserService.requireCurrentUser();
        Booking booking = bookingRepository.findByIdAndStudentForUpdate(bookingId, student.getId())
                .orElseThrow(BookingCancellationService::notFound);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleException("Só é possível cancelar aulas confirmadas");
        }
        requireNotStarted(booking);
        Instant refundDeadline = booking.getLessonSlot().getStartsAt().minus(FULL_REFUND_NOTICE);
        cancel(booking, student, !clock.instant().isAfter(refundDeadline));
        return BookingResponse.from(booking, null);
    }

    @Transactional
    public BookingResponse cancelByClub(long clubId, long bookingId) {
        access.requireClubAdmin(clubId);
        User admin = currentUserService.requireCurrentUser();
        Booking booking = bookingRepository.findActiveForUpdate(bookingId)
                .orElseThrow(BookingCancellationService::notFound);
        if (booking.getLessonSlot().getClubCoach().getClub().getId() != clubId) {
            throw new ForbiddenException("Esta reserva é de outro clube");
        }
        return cancelWithFullRefund(booking, admin);
    }

    @Transactional
    public BookingResponse cancelByCoach(long bookingId) {
        Coach coach = access.requireCurrentCoach();
        Booking booking = bookingRepository.findActiveForUpdate(bookingId)
                .orElseThrow(BookingCancellationService::notFound);
        if (!booking.getLessonSlot().getCoach().getId().equals(coach.getId())) {
            throw new ForbiddenException("Esta aula é de outro professor");
        }
        return cancelWithFullRefund(booking, coach.getUser());
    }

    /**
     * Cancela uma reserva já travada, devolvendo o valor integral se {@code refund}. Reserva segurada (Pix ainda
     * não pago) só libera a vaga: não há o que devolver, e um pagamento tardio é reembolsado pelo webhook.
     */
    public void cancel(Booking booking, User actor, boolean refund) {
        if (booking.getStatus() == BookingStatus.HELD) {
            bookingService.releaseHold(booking, BookingStatus.CANCELLED);
        } else {
            booking.setStatus(BookingStatus.CANCELLED);
            if (refund) {
                refundService.requestRefund(booking);
            }
        }
        booking.setCancelledBy(actor);
        booking.setCancelledAt(clock.instant());
        if (booking.getRefundAmountCents() == null) {
            booking.setRefundAmountCents(0L);
        }
    }

    private BookingResponse cancelWithFullRefund(Booking booking, User actor) {
        if (booking.getStatus() != BookingStatus.HELD && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleException("Esta reserva não pode ser cancelada");
        }
        requireNotStarted(booking);
        cancel(booking, actor, true);
        return BookingResponse.from(booking, null);
    }

    private void requireNotStarted(Booking booking) {
        if (!clock.instant().isBefore(booking.getLessonSlot().getStartsAt())) {
            throw new BusinessRuleException("A aula já começou");
        }
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Reserva não encontrada");
    }
}
