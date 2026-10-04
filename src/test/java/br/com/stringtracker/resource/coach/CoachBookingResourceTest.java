package br.com.stringtracker.resource.coach;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.model.schedule.RefundStatus;
import br.com.stringtracker.repository.BookingRepository;
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
class CoachBookingResourceTest {

    private static final String COACH = "kc-coachbook-coach";
    private static final String OTHER_COACH = "kc-coachbook-other-coach";
    private static final String STUDENT = "kc-coachbook-student";
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final ClubCredentials CREDENTIALS = new ClubCredentials("club-token");

    @InjectMock
    PaymentGateway gateway;

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    BookingRepository bookingRepository;

    private ClubCoach link;
    private ClubCoach otherCoachLink;

    /** COACH e OTHER_COACH atendem no mesmo clube; STUDENT é só um aluno. */
    @BeforeEach
    void seed() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
        when(gateway.refreshIfNeeded(any(Club.class))).thenReturn(CREDENTIALS);
        QuarkusTransaction.requiringNew().run(() -> {
            Club club = fixtures.connectPayments(fixtures.club("Reservas Professor"), NOW.plus(Duration.ofDays(30)));
            fixtures.user(STUDENT);
            link = fixtures.link(club, fixtures.coach(fixtures.user(COACH)), 9000L, 12000L);
            otherCoachLink = fixtures.link(club, fixtures.coach(fixtures.user(OTHER_COACH)), 9000L, 12000L);
        });
    }

    private record Created(long bookingId, String orderId) {
    }

    private Created confirmedPix(ClubCoach of, Duration startsIn) {
        return QuarkusTransaction.requiringNew().call(() -> {
            String orderId = "ORD-" + UUID.randomUUID();
            Booking booking = fixtures.pixBooking(fixtures.slot(of, NOW.plus(startsIn)), fixtures.user(STUDENT),
                    BookingStatus.CONFIRMED, PaymentStatus.APPROVED, orderId);
            return new Created(booking.getId(), orderId);
        });
    }

    private <T> T inBooking(long bookingId, Function<Booking, T> read) {
        return QuarkusTransaction.requiringNew().call(() -> read.apply(bookingRepository.findById(bookingId)));
    }

    private static String cancel(long bookingId) {
        return "/api/coach/me/bookings/" + bookingId + "/cancel";
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void cancelWithLessThan24Hours_stillRefundsInFullAndRecordsTheCoach() {
        Created lesson = confirmedPix(link, Duration.ofHours(3));

        given().when().post(cancel(lesson.bookingId()))
                .then().statusCode(200)
                .body("status", equalTo("CANCELLED"))
                .body("refundStatus", equalTo("DONE"))
                .body("refundAmountCents", equalTo(9000));

        verify(gateway).refund(CREDENTIALS, lesson.orderId(), "refund-" + lesson.bookingId());
        inBooking(lesson.bookingId(), booking -> {
            assertEquals(COACH, booking.getCancelledBy().getKeycloakId());
            assertEquals(RefundStatus.DONE, booking.getRefundStatus());
            return null;
        });
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void anotherCoachsLesson_gets403AndNothingIsRefunded() {
        Created lesson = confirmedPix(otherCoachLink, Duration.ofDays(2));

        given().when().post(cancel(lesson.bookingId())).then().statusCode(403);

        assertEquals(BookingStatus.CONFIRMED, inBooking(lesson.bookingId(), Booking::getStatus));
        verify(gateway, never()).refund(any(), anyString(), anyString());
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void userWhoIsNotACoach_gets403() {
        Created lesson = confirmedPix(link, Duration.ofDays(2));

        given().when().post(cancel(lesson.bookingId())).then().statusCode(403);

        assertEquals(BookingStatus.CONFIRMED, inBooking(lesson.bookingId(), Booking::getStatus));
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void offlineBooking_cancelsWithoutAnyRefund() {
        long bookingId = QuarkusTransaction.requiringNew().call(() -> fixtures.booking(
                fixtures.slot(link, NOW.plus(Duration.ofDays(2))), BookingStatus.CONFIRMED, fixtures.user(COACH))
                .getId());

        given().when().post(cancel(bookingId))
                .then().statusCode(200)
                .body("status", equalTo("CANCELLED"))
                .body("refundStatus", equalTo("NONE"));

        verify(gateway, never()).refund(any(), anyString(), anyString());
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void unknownAlreadyCancelledOrStartedBooking_isRejected() {
        Created cancelled = confirmedPix(link, Duration.ofDays(2));
        given().when().post(cancel(cancelled.bookingId())).then().statusCode(200);
        Created started = confirmedPix(link, Duration.ofMinutes(-10));

        given().when().post(cancel(999999999L)).then().statusCode(404);
        given().when().post(cancel(cancelled.bookingId()))
                .then().statusCode(422).body(equalTo("Esta reserva não pode ser cancelada"));
        given().when().post(cancel(started.bookingId()))
                .then().statusCode(422).body(equalTo("A aula já começou"));

        verify(gateway).refund(any(), anyString(), anyString());
    }
}
