package br.com.stringtracker.service.job;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.Payment;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.model.schedule.RefundStatus;
import br.com.stringtracker.repository.PaymentRepository;
import br.com.stringtracker.service.ClockProducer;
import br.com.stringtracker.service.PaymentProviderUnavailableException;
import br.com.stringtracker.service.payment.PaymentGateway;
import br.com.stringtracker.service.payment.PaymentGateway.ClubCredentials;
import br.com.stringtracker.service.payment.RefundService;
import br.com.stringtracker.support.ScheduleFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class RefundRetryJobTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final ClubCredentials CREDENTIALS = new ClubCredentials("club-token");

    @InjectMock
    PaymentGateway gateway;

    @Inject
    RefundRetryJob job;

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
        when(gateway.refreshIfNeeded(any(Club.class))).thenReturn(CREDENTIALS);
    }

    /** Reserva cancelada com reembolso pendente: já {@code attempts} tentativas, próxima em {@code nextAt}. */
    private long pendingRefund(String orderId, int attempts, Instant nextAt) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Club club = fixtures.connectPayments(fixtures.club("Retentativa Clube"), NOW.plus(Duration.ofDays(30)));
            ClubCoach link = fixtures.link(club, fixtures.coach(), 9000L, null);
            LessonSlot slot = fixtures.slot(link, NOW.plus(Duration.ofDays(2)));
            Booking booking = fixtures.pixBooking(slot, fixtures.user("kc-retry-student"), BookingStatus.CANCELLED,
                    PaymentStatus.APPROVED, orderId);
            booking.setRefundStatus(RefundStatus.PENDING);
            booking.setRefundAmountCents(9000L);
            Payment payment = paymentRepository.findByBookingId(booking.getId()).orElseThrow();
            payment.setRefundAttempts(attempts);
            payment.setNextRefundAt(nextAt);
            return booking.getId();
        });
    }

    private void inPayment(long bookingId, Consumer<Payment> check) {
        QuarkusTransaction.requiringNew().run(() -> {
            Payment payment = paymentRepository.findByBookingId(bookingId).orElseThrow();
            payment.getBooking().getRefundStatus();
            check.accept(payment);
        });
    }

    private static String newOrderId() {
        return "ORD-" + UUID.randomUUID();
    }

    @Test
    void dueRefund_thatNowSucceeds_isDone() {
        String orderId = newOrderId();
        long bookingId = pendingRefund(orderId, 1, NOW.minus(Duration.ofMinutes(1)));

        job.retryDue();

        verify(gateway).refund(CREDENTIALS, orderId, "refund-" + bookingId);
        inPayment(bookingId, payment -> {
            assertEquals(RefundStatus.DONE, payment.getBooking().getRefundStatus());
            assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
            assertEquals(2, payment.getRefundAttempts());
            assertNull(payment.getNextRefundAt());
        });
    }

    @Test
    void refundNotDueYet_isLeftAlone() {
        String orderId = newOrderId();
        long bookingId = pendingRefund(orderId, 1, NOW.plus(Duration.ofMinutes(5)));

        job.retryDue();

        verify(gateway, never()).refund(any(), eq(orderId), anyString());
        inPayment(bookingId, payment -> {
            assertEquals(RefundStatus.PENDING, payment.getBooking().getRefundStatus());
            assertEquals(1, payment.getRefundAttempts());
        });
    }

    @Test
    void failingAgain_countsTheAttemptAndSchedulesTheNextOneFifteenMinutesAhead() {
        String orderId = newOrderId();
        long bookingId = pendingRefund(orderId, 3, NOW.minus(Duration.ofMinutes(1)));
        doThrow(new PaymentProviderUnavailableException("fora do ar", null))
                .when(gateway).refund(any(), eq(orderId), anyString());

        job.retryDue();

        inPayment(bookingId, payment -> {
            assertEquals(RefundStatus.PENDING, payment.getBooking().getRefundStatus());
            assertEquals(4, payment.getRefundAttempts());
            assertEquals(NOW.plus(Duration.ofMinutes(15)), payment.getNextRefundAt());
        });
    }

    @Test
    void after24HoursOfAttempts_theRefundFailsForGood_andIsNotRetriedAgain() {
        String orderId = newOrderId();
        // A tentativa inicial mais 95 retentativas já falharam; a 97ª é a última.
        long bookingId = pendingRefund(orderId, RefundService.MAX_ATTEMPTS - 1, NOW.minus(Duration.ofMinutes(1)));
        doThrow(new PaymentProviderUnavailableException("fora do ar", null))
                .when(gateway).refund(any(), eq(orderId), anyString());

        job.retryDue();
        job.retryDue();

        inPayment(bookingId, payment -> {
            assertEquals(RefundStatus.FAILED, payment.getBooking().getRefundStatus());
            assertEquals(RefundService.MAX_ATTEMPTS, payment.getRefundAttempts());
            assertNull(payment.getNextRefundAt());
        });
        verify(gateway).refund(any(), eq(orderId), anyString());
    }

    @Test
    void ninetySevenAttempts_spanTwentyFourHoursAtFifteenMinuteIntervals() {
        assertEquals(97, RefundService.MAX_ATTEMPTS);
        assertEquals(Duration.ofHours(24), RefundService.RETRY_INTERVAL.multipliedBy(RefundService.MAX_ATTEMPTS - 1));
    }
}
