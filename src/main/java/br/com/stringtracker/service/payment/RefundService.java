package br.com.stringtracker.service.payment;

import br.com.stringtracker.model.User;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.Payment;
import br.com.stringtracker.model.schedule.PaymentMode;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.model.schedule.RefundStatus;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.PaymentRepository;
import br.com.stringtracker.service.BusinessRuleException;
import br.com.stringtracker.service.LessonNotifier;
import br.com.stringtracker.service.PaymentProviderException;
import br.com.stringtracker.service.PaymentProviderUnavailableException;
import br.com.stringtracker.service.payment.PaymentGateway.ClubCredentials;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Reembolso total de reserva paga com Pix, sem perder reembolso que falhou (CANC-05): a falha deixa a reserva
 * com {@code refund_status = PENDING} e o {@code RefundRetryJob} tenta de novo a cada 15 minutos. Passadas
 * 24 horas de tentativas (a inicial mais 96 retentativas) o reembolso vira {@code FAILED}, e o admin do clube pode
 * reiniciar as tentativas ({@link #restart}) ou marcá-lo como resolvido por fora ({@link #resolveManually}).
 */
@ApplicationScoped
public class RefundService {

    public static final Duration RETRY_INTERVAL = Duration.ofMinutes(15);
    public static final int MAX_ATTEMPTS = 1 + (int) (Duration.ofHours(24).dividedBy(RETRY_INTERVAL));

    private static final Logger LOG = Logger.getLogger(RefundService.class);

    @Inject
    BookingRepository bookingRepository;

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    PaymentGateway gateway;

    @Inject
    LessonNotifier notifier;

    @Inject
    Clock clock;

    /**
     * Reembolsa o valor integral. Não faz nada para reserva paga por fora, sem pagamento aprovado ou cujo
     * reembolso já foi pedido. Falha do provedor não lança: deixa o reembolso pendente para nova tentativa.
     */
    @Transactional
    public void requestRefund(Booking booking) {
        if (booking.getPaymentMode() != PaymentMode.PIX || booking.getRefundStatus() != RefundStatus.NONE) {
            return;
        }
        paymentRepository.findByBookingId(booking.getId())
                .filter(payment -> payment.getStatus() == PaymentStatus.APPROVED)
                .ifPresent(payment -> {
                    booking.setRefundAmountCents(payment.getAmountCents());
                    attempt(booking, payment);
                });
    }

    /** Nova tentativa de um reembolso pendente; ignora o que já foi resolvido por outra via. */
    @Transactional
    public void retry(long paymentId) {
        paymentRepository.findBookingIdByPaymentId(paymentId).ifPresent(bookingId -> {
            Booking booking = bookingRepository.findById(bookingId, LockModeType.PESSIMISTIC_WRITE);
            if (booking.getRefundStatus() == RefundStatus.PENDING) {
                attemptAndNotify(booking, paymentRepository.findByBookingId(bookingId).orElseThrow());
            }
        });
    }

    /**
     * O admin reinicia as tentativas de um reembolso FAILED: a contagem volta a zero e já há uma tentativa agora. Se
     * ela falha, o reembolso volta a PENDING e o job retoma de 15 em 15 minutos. A reserva vem travada.
     */
    @Transactional
    public void restart(Booking booking) {
        requireFailed(booking);
        Payment payment = paymentRepository.findByBookingId(booking.getId()).orElseThrow();
        payment.setRefundAttempts(0);
        attemptAndNotify(booking, payment);
    }

    /** O admin deu o reembolso FAILED por resolvido fora do provedor: guarda quem e quando. A reserva vem travada. */
    @Transactional
    public void resolveManually(Booking booking, User admin) {
        requireFailed(booking);
        booking.setRefundStatus(RefundStatus.RESOLVED_MANUALLY);
        booking.setRefundResolvedBy(admin);
        booking.setRefundResolvedAt(clock.instant());
    }

    private static void requireFailed(Booking booking) {
        if (booking.getRefundStatus() != RefundStatus.FAILED) {
            throw new BusinessRuleException("Só um reembolso que falhou pode ser reiniciado ou resolvido manualmente");
        }
    }

    private void attemptAndNotify(Booking booking, Payment payment) {
        attempt(booking, payment);
        if (booking.getRefundStatus() == RefundStatus.DONE) {
            notifier.refundCompleted(booking);
        }
    }

    private void attempt(Booking booking, Payment payment) {
        Instant now = clock.instant();
        payment.setRefundAttempts(payment.getRefundAttempts() + 1);
        try {
            ClubCredentials credentials = gateway.refreshIfNeeded(booking.getLessonSlot().getClubCoach().getClub());
            gateway.refund(credentials, payment.getProviderPaymentId(), "refund-" + booking.getId());
        } catch (PaymentProviderException | PaymentProviderUnavailableException | BusinessRuleException e) {
            LOG.warnf("Reembolso da reserva %d falhou (tentativa %d): %s", booking.getId(),
                    payment.getRefundAttempts(), e.getMessage());
            if (payment.getRefundAttempts() >= MAX_ATTEMPTS) {
                booking.setRefundStatus(RefundStatus.FAILED);
                payment.setNextRefundAt(null);
            } else {
                booking.setRefundStatus(RefundStatus.PENDING);
                payment.setNextRefundAt(now.plus(RETRY_INTERVAL));
            }
            return;
        }
        booking.setRefundStatus(RefundStatus.DONE);
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setNextRefundAt(null);
    }
}
