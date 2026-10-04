package br.com.stringtracker.service.job;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.User;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.Payment;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.LessonSlotRepository;
import br.com.stringtracker.repository.PaymentRepository;
import br.com.stringtracker.service.BusinessRuleException;
import br.com.stringtracker.service.ClockProducer;
import br.com.stringtracker.service.PaymentProviderUnavailableException;
import br.com.stringtracker.service.payment.PaymentGateway;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class HoldExpiryJobTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final ClubCredentials CREDENTIALS = new ClubCredentials("club-token");

    @InjectMock
    PaymentGateway gateway;

    @Inject
    HoldExpiryJob job;

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    LessonSlotRepository lessonSlotRepository;

    @Inject
    PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
        when(gateway.refreshIfNeeded(any(Club.class))).thenReturn(CREDENTIALS);
    }

    private record Seed(long slotId, long bookingId, String orderId) {
    }

    /** Reserva Pix do horário com o hold vencendo em {@code holdExpiresAt}. */
    private Seed heldBooking(BookingStatus status, Instant holdExpiresAt) {
        String orderId = "ORD-" + UUID.randomUUID();
        return QuarkusTransaction.requiringNew().call(() -> {
            Club club = fixtures.connectPayments(fixtures.club("Expiração Clube"), NOW.plus(Duration.ofDays(30)));
            ClubCoach link = fixtures.link(club, fixtures.coach(), 9000L, null);
            LessonSlot slot = fixtures.slot(link, NOW.plus(Duration.ofDays(2)));
            User student = fixtures.user("kc-expiry-student");
            PaymentStatus paymentStatus = status == BookingStatus.HELD ? PaymentStatus.PENDING : PaymentStatus.APPROVED;
            Booking booking = fixtures.pixBooking(slot, student, status, paymentStatus, orderId);
            booking.setHoldExpiresAt(holdExpiresAt);
            return new Seed(slot.getId(), booking.getId(), orderId);
        });
    }

    private void check(long bookingId, Consumer<Booking> bookingCheck, Consumer<Payment> paymentCheck) {
        QuarkusTransaction.requiringNew().run(() -> {
            bookingCheck.accept(bookingRepository.findById(bookingId));
            paymentCheck.accept(paymentRepository.findByBookingId(bookingId).orElseThrow());
        });
    }

    @Test
    void expiredHold_expiresTheBookingAndThePayment_freesTheSeat_andCancelsThePixAtTheProvider() {
        Seed seed = heldBooking(BookingStatus.HELD, NOW.minus(Duration.ofSeconds(1)));

        job.expireDue();

        verify(gateway).cancel(CREDENTIALS, seed.orderId(), "cancel-" + seed.bookingId());
        check(seed.bookingId(), b -> assertEquals(BookingStatus.EXPIRED, b.getStatus()),
                p -> assertEquals(PaymentStatus.EXPIRED, p.getStatus()));
        // A vaga aceita nova reserva: o índice único só vale para HELD e CONFIRMED.
        QuarkusTransaction.requiringNew().run(() -> fixtures.studentBooking(
                lessonSlotRepository.findById(seed.slotId()), BookingStatus.HELD, fixtures.user("kc-expiry-other")));
    }

    @Test
    void failureToCancelAtTheProvider_doesNotPreventTheExpiration() {
        Seed unavailable = heldBooking(BookingStatus.HELD, NOW.minus(Duration.ofMinutes(1)));
        doThrow(new PaymentProviderUnavailableException("fora do ar", null))
                .when(gateway).cancel(any(), eq(unavailable.orderId()), anyString());
        Seed disconnected = heldBooking(BookingStatus.HELD, NOW.minus(Duration.ofMinutes(1)));
        doThrow(new BusinessRuleException("sem conta"))
                .when(gateway).cancel(any(), eq(disconnected.orderId()), anyString());
        Seed unexpected = heldBooking(BookingStatus.HELD, NOW.minus(Duration.ofMinutes(1)));
        doThrow(new IllegalStateException("bug"))
                .when(gateway).cancel(any(), eq(unexpected.orderId()), anyString());

        job.expireDue();

        for (Seed seed : new Seed[]{unavailable, disconnected, unexpected}) {
            check(seed.bookingId(), b -> assertEquals(BookingStatus.EXPIRED, b.getStatus()),
                    p -> assertEquals(PaymentStatus.EXPIRED, p.getStatus()));
        }
    }

    @Test
    void holdNotYetExpired_andConfirmedBookings_areLeftAlone() {
        Seed running = heldBooking(BookingStatus.HELD, NOW.plus(Duration.ofMinutes(3)));
        Seed exactlyNow = heldBooking(BookingStatus.HELD, NOW);
        Seed confirmed = heldBooking(BookingStatus.CONFIRMED, NOW.minus(Duration.ofHours(1)));

        job.expireDue();

        verify(gateway, never()).cancel(any(), anyString(), anyString());
        check(running.bookingId(), b -> assertEquals(BookingStatus.HELD, b.getStatus()),
                p -> assertEquals(PaymentStatus.PENDING, p.getStatus()));
        check(exactlyNow.bookingId(), b -> assertEquals(BookingStatus.HELD, b.getStatus()),
                p -> assertEquals(PaymentStatus.PENDING, p.getStatus()));
        check(confirmed.bookingId(), b -> assertEquals(BookingStatus.CONFIRMED, b.getStatus()),
                p -> assertEquals(PaymentStatus.APPROVED, p.getStatus()));
    }

    @Test
    void runningTwice_expiresOnceAndCancelsOnce() {
        Seed seed = heldBooking(BookingStatus.HELD, NOW.minus(Duration.ofMinutes(1)));

        job.expireDue();
        job.expireDue();

        verify(gateway).cancel(CREDENTIALS, seed.orderId(), "cancel-" + seed.bookingId());
        check(seed.bookingId(), b -> assertEquals(BookingStatus.EXPIRED, b.getStatus()),
                p -> assertEquals(PaymentStatus.EXPIRED, p.getStatus()));
    }
}
