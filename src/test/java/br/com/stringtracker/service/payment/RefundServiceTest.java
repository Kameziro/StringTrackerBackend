package br.com.stringtracker.service.payment;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.User;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.Payment;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.model.schedule.RefundStatus;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.PaymentRepository;
import br.com.stringtracker.service.BusinessRuleException;
import br.com.stringtracker.service.ClockProducer;
import br.com.stringtracker.service.PaymentProviderException;
import br.com.stringtracker.service.PaymentProviderUnavailableException;
import br.com.stringtracker.service.payment.PaymentGateway.ClubCredentials;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class RefundServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final ClubCredentials CREDENTIALS = new ClubCredentials("club-token");

    @InjectMock
    PaymentGateway gateway;

    @Inject
    RefundService refundService;

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
        when(gateway.refreshIfNeeded(any(Club.class))).thenReturn(CREDENTIALS);
    }

    private long approvedPixBooking(String orderId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Club club = fixtures.connectPayments(fixtures.club("Reembolso Clube"), NOW.plus(Duration.ofDays(30)));
            ClubCoach link = fixtures.link(club, fixtures.coach(), 9000L, null);
            LessonSlot slot = fixtures.slot(link, NOW.plus(Duration.ofDays(2)));
            return fixtures.pixBooking(slot, fixtures.user("kc-refund-student"), BookingStatus.CONFIRMED,
                    PaymentStatus.APPROVED, orderId).getId();
        });
    }

    private static String newOrderId() {
        return "ORD-" + UUID.randomUUID();
    }

    private void request(long bookingId) {
        QuarkusTransaction.requiringNew().run(() -> refundService.requestRefund(bookingRepository.findById(bookingId)));
    }

    private void inBooking(long bookingId, Consumer<Booking> check) {
        QuarkusTransaction.requiringNew().run(() -> check.accept(bookingRepository.findById(bookingId)));
    }

    private void inPayment(long bookingId, Consumer<Payment> check) {
        QuarkusTransaction.requiringNew().run(() ->
                check.accept(paymentRepository.findByBookingId(bookingId).orElseThrow()));
    }

    @Test
    void successfulRefund_marksTheRefundDoneAndThePaymentRefunded() {
        String orderId = newOrderId();
        long bookingId = approvedPixBooking(orderId);

        request(bookingId);

        verify(gateway).refund(CREDENTIALS, orderId, "refund-" + bookingId);
        inBooking(bookingId, booking -> {
            assertEquals(RefundStatus.DONE, booking.getRefundStatus());
            assertEquals(9000L, booking.getRefundAmountCents());
        });
        inPayment(bookingId, payment -> {
            assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
            assertEquals(1, payment.getRefundAttempts());
            assertNull(payment.getNextRefundAt());
        });
    }

    @Test
    void providerFailure_leavesTheRefundPendingWithTheNextAttemptInFifteenMinutes() {
        String orderId = newOrderId();
        long bookingId = approvedPixBooking(orderId);
        doThrow(new PaymentProviderUnavailableException("fora do ar", null))
                .when(gateway).refund(any(), anyString(), anyString());

        request(bookingId);

        inBooking(bookingId, booking -> {
            assertEquals(RefundStatus.PENDING, booking.getRefundStatus());
            assertEquals(9000L, booking.getRefundAmountCents());
        });
        inPayment(bookingId, payment -> {
            assertEquals(PaymentStatus.APPROVED, payment.getStatus());
            assertEquals(1, payment.getRefundAttempts());
            assertEquals(NOW.plus(Duration.ofMinutes(15)), payment.getNextRefundAt());
        });
    }

    @Test
    void providerRefusalAndADisconnectedClub_alsoLeaveTheRefundPending() {
        long refused = approvedPixBooking(newOrderId());
        doThrow(new PaymentProviderException("recusou", null)).when(gateway).refund(any(), anyString(), anyString());
        request(refused);

        long disconnected = approvedPixBooking(newOrderId());
        when(gateway.refreshIfNeeded(any(Club.class))).thenThrow(new BusinessRuleException("sem conta"));
        request(disconnected);

        inBooking(refused, booking -> assertEquals(RefundStatus.PENDING, booking.getRefundStatus()));
        inBooking(disconnected, booking -> assertEquals(RefundStatus.PENDING, booking.getRefundStatus()));
    }

    @Test
    void requestingAgain_afterTheRefundWasDoneOrIsPending_doesNotCallTheProviderAgain() {
        long done = approvedPixBooking(newOrderId());
        request(done);
        request(done);

        long pending = approvedPixBooking(newOrderId());
        doThrow(new PaymentProviderUnavailableException("fora do ar", null))
                .when(gateway).refund(any(), anyString(), eq("refund-" + pending));
        request(pending);
        request(pending);

        verify(gateway, times(1)).refund(any(), anyString(), eq("refund-" + done));
        verify(gateway, times(1)).refund(any(), anyString(), eq("refund-" + pending));
        inPayment(pending, payment -> assertEquals(1, payment.getRefundAttempts()));
    }

    @Test
    void paidOutsideThePlatform_orWithoutAnApprovedPayment_isNotRefunded() {
        long offline = QuarkusTransaction.requiringNew().call(() -> {
            Club club = fixtures.club("Reembolso Offline");
            ClubCoach link = fixtures.link(club, fixtures.coach(), 9000L, null);
            LessonSlot slot = fixtures.slot(link, NOW.plus(Duration.ofDays(2)));
            return fixtures.studentBooking(slot, BookingStatus.CONFIRMED, fixtures.user("kc-refund-student")).getId();
        });
        long unpaid = QuarkusTransaction.requiringNew().call(() -> {
            Club club = fixtures.club("Reembolso Pendente");
            ClubCoach link = fixtures.link(club, fixtures.coach(), 9000L, null);
            LessonSlot slot = fixtures.slot(link, NOW.plus(Duration.ofDays(2)));
            User student = fixtures.user("kc-refund-student");
            return fixtures.pixBooking(slot, student, BookingStatus.HELD, PaymentStatus.PENDING, newOrderId()).getId();
        });

        request(offline);
        request(unpaid);

        verify(gateway, never()).refund(any(), anyString(), anyString());
        inBooking(offline, booking -> assertEquals(RefundStatus.NONE, booking.getRefundStatus()));
        inBooking(unpaid, booking -> assertEquals(RefundStatus.NONE, booking.getRefundStatus()));
    }
}
