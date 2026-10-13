package br.com.stringtracker.resource;

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
import br.com.stringtracker.service.PaymentProviderUnavailableException;
import br.com.stringtracker.service.payment.PaymentGateway;
import br.com.stringtracker.service.payment.PaymentGateway.ClubCredentials;
import br.com.stringtracker.service.payment.PaymentGateway.PixCharge;
import br.com.stringtracker.support.ScheduleFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
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
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class LessonBookingCancelResourceTest {

    private static final String STUDENT = "kc-cancel-student";
    private static final String OTHER = "kc-cancel-other";
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

    private ClubCoach link;

    @BeforeEach
    void setUp() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
        when(gateway.refreshIfNeeded(any(Club.class))).thenReturn(CREDENTIALS);
        when(gateway.createPix(any(), anyLong(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenAnswer(call -> new PixCharge("ORD-" + UUID.randomUUID(), "copia", "qr", "https://mp/ticket"));
        QuarkusTransaction.requiringNew().run(() -> {
            fixtures.user(STUDENT);
            fixtures.user(OTHER);
            Club club = fixtures.connectPayments(fixtures.club("Cancela Clube"), NOW.plus(Duration.ofDays(30)));
            link = fixtures.link(club, fixtures.coach(), 9000L, 12000L);
        });
    }

    private record Created(long bookingId, long slotId, String orderId) {
    }

    /** Reserva Pix confirmada e paga (R$ 90) de uma aula que começa {@code startsIn} depois de agora. */
    private Created confirmedPix(Duration startsIn) {
        return QuarkusTransaction.requiringNew().call(() -> {
            String orderId = "ORD-" + UUID.randomUUID();
            LessonSlot slot = fixtures.slot(link, NOW.plus(startsIn));
            Booking booking = fixtures.pixBooking(slot, fixtures.user(STUDENT), BookingStatus.CONFIRMED,
                    PaymentStatus.APPROVED, orderId);
            return new Created(booking.getId(), slot.getId(), orderId);
        });
    }

    private <T> T inBooking(long bookingId, Function<Booking, T> read) {
        return QuarkusTransaction.requiringNew().call(() -> read.apply(bookingRepository.findById(bookingId)));
    }

    private static String cancel(long bookingId) {
        return "/api/lessons/bookings/" + bookingId + "/cancel";
    }

    private void assertCancelledByStudentWith(long bookingId, RefundStatus refundStatus, long refundCents) {
        inBooking(bookingId, booking -> {
            assertEquals(BookingStatus.CANCELLED, booking.getStatus());
            assertEquals(STUDENT, booking.getCancelledBy().getKeycloakId());
            assertEquals(NOW, booking.getCancelledAt());
            assertEquals(refundStatus, booking.getRefundStatus());
            assertEquals(refundCents, booking.getRefundAmountCents());
            return null;
        });
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void twentyFiveHoursBefore_refundsTheFullAmountAndRecordsWhoWhenAndHowMuch() {
        Created lesson = confirmedPix(Duration.ofHours(25));

        given().when().post(cancel(lesson.bookingId()))
                .then().statusCode(200)
                .body("status", equalTo("CANCELLED"))
                .body("refundStatus", equalTo("DONE"))
                .body("refundAmountCents", equalTo(9000))
                .body("cancelledAt", equalTo("2026-10-05T12:00:00Z"));

        verify(gateway).refund(CREDENTIALS, lesson.orderId(), "refund-" + lesson.bookingId());
        assertCancelledByStudentWith(lesson.bookingId(), RefundStatus.DONE, 9000L);
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void exactlyTwentyFourHoursBefore_stillRefundsTheFullAmount() {
        Created lesson = confirmedPix(Duration.ofHours(24));

        given().when().post(cancel(lesson.bookingId()))
                .then().statusCode(200)
                .body("refundStatus", equalTo("DONE"))
                .body("refundAmountCents", equalTo(9000));

        verify(gateway).refund(CREDENTIALS, lesson.orderId(), "refund-" + lesson.bookingId());
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void twentyThreeHoursBefore_cancelsWithoutRefundingAndKeepsThePaymentApproved() {
        Created lesson = confirmedPix(Duration.ofHours(23));

        given().when().post(cancel(lesson.bookingId()))
                .then().statusCode(200)
                .body("status", equalTo("CANCELLED"))
                .body("refundStatus", equalTo("NONE"))
                .body("refundAmountCents", equalTo(0));

        verify(gateway, never()).refund(any(), anyString(), anyString());
        assertCancelledByStudentWith(lesson.bookingId(), RefundStatus.NONE, 0L);
        QuarkusTransaction.requiringNew().run(() -> assertEquals(PaymentStatus.APPROVED,
                paymentRepository.findByBookingId(lesson.bookingId()).orElseThrow().getStatus()));
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void lessonAlreadyStarted_returns422AndKeepsTheBooking() {
        Created started = confirmedPix(Duration.ofMinutes(-60));
        Created startingNow = confirmedPix(Duration.ZERO);

        for (Created lesson : new Created[]{started, startingNow}) {
            given().when().post(cancel(lesson.bookingId()))
                    .then().statusCode(422)
                    .body(equalTo("A aula já começou"));
            assertEquals(BookingStatus.CONFIRMED, inBooking(lesson.bookingId(), Booking::getStatus));
        }
        verify(gateway, never()).refund(any(), anyString(), anyString());
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void moreThanTwoHoursBefore_freesTheSlotForAnotherStudent() {
        Created lesson = confirmedPix(Duration.ofHours(25));
        given().when().post(cancel(lesson.bookingId())).then().statusCode(200);

        given().contentType(ContentType.JSON).body("{\"slotId\":%d,\"type\":\"SINGLES\"}".formatted(lesson.slotId()))
                .when().post("/api/lessons/bookings")
                .then().statusCode(201)
                .body("status", equalTo("HELD"));
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void lessThanTwoHoursBefore_theSlotCannotBeBookedAgain() {
        Created lesson = confirmedPix(Duration.ofMinutes(90));
        given().when().post(cancel(lesson.bookingId())).then().statusCode(200).body("refundStatus", equalTo("NONE"));

        given().contentType(ContentType.JSON).body("{\"slotId\":%d,\"type\":\"SINGLES\"}".formatted(lesson.slotId()))
                .when().post("/api/lessons/bookings")
                .then().statusCode(422)
                .body(equalTo("Reservas fecham 2h antes da aula"));
    }

    @Test
    @TestSecurity(user = OTHER)
    @JwtSecurity(claims = {@Claim(key = "sub", value = OTHER)})
    void someoneElsesBookingOrAnUnknownOne_returns404() {
        Created lesson = confirmedPix(Duration.ofHours(25));

        given().when().post(cancel(lesson.bookingId())).then().statusCode(404);
        given().when().post(cancel(999999999L)).then().statusCode(404);

        assertEquals(BookingStatus.CONFIRMED, inBooking(lesson.bookingId(), Booking::getStatus));
        verify(gateway, never()).refund(any(), anyString(), anyString());
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void heldOrAlreadyCancelledBooking_returns422AndRefundsOnlyOnce() {
        Created held = QuarkusTransaction.requiringNew().call(() -> {
            LessonSlot slot = fixtures.slot(link, NOW.plus(Duration.ofDays(2)));
            Booking booking = fixtures.pixBooking(slot, fixtures.user(STUDENT), BookingStatus.HELD,
                    PaymentStatus.PENDING, "ORD-" + UUID.randomUUID());
            return new Created(booking.getId(), slot.getId(), null);
        });
        Created lesson = confirmedPix(Duration.ofHours(30));
        given().when().post(cancel(lesson.bookingId())).then().statusCode(200);

        given().when().post(cancel(held.bookingId()))
                .then().statusCode(422).body(equalTo("Só é possível cancelar aulas confirmadas"));
        given().when().post(cancel(lesson.bookingId()))
                .then().statusCode(422).body(equalTo("Só é possível cancelar aulas confirmadas"));

        verify(gateway).refund(any(), anyString(), anyString());
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void providerRefundFailure_cancelsTheLessonAndLeavesTheRefundPending() {
        doThrow(new PaymentProviderUnavailableException("timeout", null)).when(gateway)
                .refund(any(), anyString(), anyString());
        Created lesson = confirmedPix(Duration.ofHours(48));

        given().when().post(cancel(lesson.bookingId()))
                .then().statusCode(200)
                .body("status", equalTo("CANCELLED"))
                .body("refundStatus", equalTo("PENDING"))
                .body("refundAmountCents", equalTo(9000))
                .body("cancelledAt", notNullValue());

        assertCancelledByStudentWith(lesson.bookingId(), RefundStatus.PENDING, 9000L);
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void manualBookingLinkedToTheStudent_cancelsWithoutAnyRefund() {
        long bookingId = QuarkusTransaction.requiringNew().call(() -> fixtures.studentBooking(
                fixtures.slot(link, NOW.plus(Duration.ofDays(3))), BookingStatus.CONFIRMED, fixtures.user(STUDENT))
                .getId());

        given().when().post(cancel(bookingId))
                .then().statusCode(200)
                .body("status", equalTo("CANCELLED"))
                .body("refundStatus", equalTo("NONE"));

        verify(gateway, never()).refund(any(), anyString(), anyString());
    }
}
