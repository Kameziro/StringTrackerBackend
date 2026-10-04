package br.com.stringtracker.resource.admin;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.model.schedule.RefundStatus;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.PaymentRepository;
import br.com.stringtracker.service.ClockProducer;
import br.com.stringtracker.service.payment.PaymentGateway;
import br.com.stringtracker.service.payment.PaymentGateway.ClubCredentials;
import br.com.stringtracker.support.ScheduleFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class BookingAdminResourceTest {

    private static final String ADMIN = "kc-book-admin";
    private static final String OTHER_ADMIN = "kc-book-other-admin";
    private static final String STUDENT = "kc-book-admin-student";
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final ClubCredentials CREDENTIALS = new ClubCredentials("club-token");

    @InjectMock
    PaymentGateway gateway;

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    PaymentRepository paymentRepository;

    private long clubId;
    private long otherClubId;
    private ClubCoach link;

    /** ADMIN administra o clube do professor; OTHER_ADMIN administra outro clube. */
    @BeforeEach
    void seed() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
        when(gateway.refreshIfNeeded(any(Club.class))).thenReturn(CREDENTIALS);
        QuarkusTransaction.requiringNew().run(() -> {
            Club club = fixtures.connectPayments(fixtures.club("Reservas Admin"), NOW.plus(Duration.ofDays(30)));
            Club other = fixtures.club("Reservas Outro");
            fixtures.admin(club, fixtures.user(ADMIN));
            fixtures.admin(other, fixtures.user(OTHER_ADMIN));
            fixtures.user(STUDENT);
            link = fixtures.link(club, fixtures.coach(), 9000L, 12000L);
            clubId = club.getId();
            otherClubId = other.getId();
        });
    }

    private record Created(long bookingId, String orderId) {
    }

    private Created confirmedPix(Duration startsIn) {
        return QuarkusTransaction.requiringNew().call(() -> {
            String orderId = "ORD-" + UUID.randomUUID();
            Booking booking = fixtures.pixBooking(fixtures.slot(link, NOW.plus(startsIn)), fixtures.user(STUDENT),
                    BookingStatus.CONFIRMED, PaymentStatus.APPROVED, orderId);
            return new Created(booking.getId(), orderId);
        });
    }

    private <T> T inBooking(long bookingId, Function<Booking, T> read) {
        return QuarkusTransaction.requiringNew().call(() -> read.apply(bookingRepository.findById(bookingId)));
    }

    private String cancel(long club, long bookingId) {
        return "/api/admin/clubs/" + club + "/bookings/" + bookingId + "/cancel";
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void cancelWithLessThan24Hours_stillRefundsInFullAndRecordsTheAdmin() {
        Created lesson = confirmedPix(Duration.ofHours(3));

        given().when().post(cancel(clubId, lesson.bookingId()))
                .then().statusCode(200)
                .body("status", equalTo("CANCELLED"))
                .body("refundStatus", equalTo("DONE"))
                .body("refundAmountCents", equalTo(9000));

        verify(gateway).refund(CREDENTIALS, lesson.orderId(), "refund-" + lesson.bookingId());
        inBooking(lesson.bookingId(), booking -> {
            assertEquals(ADMIN, booking.getCancelledBy().getKeycloakId());
            assertEquals(NOW, booking.getCancelledAt());
            assertEquals(RefundStatus.DONE, booking.getRefundStatus());
            return null;
        });
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void offlineBooking_cancelsWithoutAnyRefund() {
        long bookingId = QuarkusTransaction.requiringNew().call(() -> fixtures.booking(
                fixtures.slot(link, NOW.plus(Duration.ofDays(2))), BookingStatus.CONFIRMED, fixtures.user(ADMIN))
                .getId());

        given().when().post(cancel(clubId, bookingId))
                .then().statusCode(200)
                .body("status", equalTo("CANCELLED"))
                .body("refundStatus", equalTo("NONE"))
                .body("refundAmountCents", equalTo(0));

        verify(gateway, never()).refund(any(), anyString(), anyString());
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void heldBooking_releasesTheSeatAndCancelsThePixAtTheProvider() {
        String orderId = "ORD-" + UUID.randomUUID();
        long bookingId = QuarkusTransaction.requiringNew().call(() -> fixtures.pixBooking(
                fixtures.slot(link, NOW.plus(Duration.ofDays(2))), fixtures.user(STUDENT), BookingStatus.HELD,
                PaymentStatus.PENDING, orderId).getId());

        given().when().post(cancel(clubId, bookingId)).then().statusCode(200).body("status", equalTo("CANCELLED"));

        verify(gateway).cancel(CREDENTIALS, orderId, "cancel-" + bookingId);
        verify(gateway, never()).refund(any(), anyString(), anyString());
        QuarkusTransaction.requiringNew().run(() -> assertEquals(PaymentStatus.EXPIRED,
                paymentRepository.findByBookingId(bookingId).orElseThrow().getStatus()));
    }

    @Test
    @TestSecurity(user = OTHER_ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = OTHER_ADMIN)})
    void adminOfAnotherClub_gets403_bothForTheBookingsClubAndForTheirOwn() {
        Created lesson = confirmedPix(Duration.ofDays(2));

        given().when().post(cancel(clubId, lesson.bookingId())).then().statusCode(403);
        given().when().post(cancel(otherClubId, lesson.bookingId())).then().statusCode(403);

        assertEquals(BookingStatus.CONFIRMED, inBooking(lesson.bookingId(), Booking::getStatus));
        verify(gateway, never()).refund(any(), anyString(), anyString());
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void userWhoIsNotAnAdmin_gets403() {
        Created lesson = confirmedPix(Duration.ofDays(2));

        given().when().post(cancel(clubId, lesson.bookingId())).then().statusCode(403);

        assertEquals(BookingStatus.CONFIRMED, inBooking(lesson.bookingId(), Booking::getStatus));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void unknownAlreadyCancelledExpiredOrStartedBooking_isRejected() {
        Created cancelled = confirmedPix(Duration.ofDays(2));
        given().when().post(cancel(clubId, cancelled.bookingId())).then().statusCode(200);
        Created started = confirmedPix(Duration.ofMinutes(-10));
        long expired = QuarkusTransaction.requiringNew().call(() -> fixtures.studentBooking(
                fixtures.slot(link, NOW.plus(Duration.ofDays(3))), BookingStatus.EXPIRED, fixtures.user(STUDENT))
                .getId());

        given().when().post(cancel(clubId, 999999999L)).then().statusCode(404);
        given().when().post(cancel(clubId, cancelled.bookingId()))
                .then().statusCode(422).body(equalTo("Esta reserva não pode ser cancelada"));
        given().when().post(cancel(clubId, expired))
                .then().statusCode(422).body(equalTo("Esta reserva não pode ser cancelada"));
        given().when().post(cancel(clubId, started.bookingId()))
                .then().statusCode(422).body(equalTo("A aula já começou"));

        verify(gateway).refund(any(), anyString(), anyString());
    }
}
