package br.com.stringtracker.resource;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonSlotStatus;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.DayBlockRepository;
import br.com.stringtracker.repository.LessonSlotRepository;
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
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Function;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class DayBlockResourceTest {

    private static final String ADMIN = "kc-dayblock-admin";
    private static final String OTHER_ADMIN = "kc-dayblock-other-admin";
    private static final String COACH = "kc-dayblock-coach";
    private static final String STUDENT = "kc-dayblock-student";
    private static final String OTHER_STUDENT = "kc-dayblock-other-student";

    /** Segunda-feira 2026-10-05, 09:00 em São Paulo. O dia bloqueado é a quarta-feira 2026-10-07. */
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final String DAY = "2026-10-07";
    private static final Instant WEDNESDAY_10H = Instant.parse("2026-10-07T13:00:00Z");
    private static final ClubCredentials CREDENTIALS = new ClubCredentials("club-token");

    @InjectMock
    PaymentGateway gateway;

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    LessonSlotRepository lessonSlotRepository;

    @Inject
    DayBlockRepository dayBlockRepository;

    @Inject
    PaymentRepository paymentRepository;

    private long clubAId;
    private long clubBId;
    private long coachId;
    private ClubCoach linkA;
    private ClubCoach linkB;
    private long clubASlotId;
    private long clubAFreeSlotId;
    private long clubBSlotId;
    private long clubABookingId;
    private long clubBBookingId;
    private String clubAOrderId;

    /**
     * O professor atende nos clubes A e B. Na quarta-feira: no clube A um horário reservado (10h) e um livre (11h); no
     * clube B um horário reservado (15h). Os dois clubes têm Pix conectado; ADMIN administra só o A.
     */
    @BeforeEach
    void seed() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
        when(gateway.refreshIfNeeded(any(Club.class))).thenReturn(CREDENTIALS);
        QuarkusTransaction.requiringNew().run(() -> {
            Club clubA = fixtures.connectPayments(fixtures.club("Bloqueio Clube A"), NOW.plus(Duration.ofDays(30)));
            Club clubB = fixtures.connectPayments(fixtures.club("Bloqueio Clube B"), NOW.plus(Duration.ofDays(30)));
            fixtures.admin(clubA, fixtures.user(ADMIN));
            fixtures.admin(clubB, fixtures.user(OTHER_ADMIN));
            fixtures.user(STUDENT);
            fixtures.user(OTHER_STUDENT);
            Coach coach = fixtures.coach(fixtures.user(COACH));
            dayBlockRepository.delete("coach.id", coach.getId());
            linkA = fixtures.link(clubA, coach, 9000L, 12000L);
            linkB = fixtures.link(clubB, coach, 9000L, 12000L);

            clubAOrderId = "ORD-" + UUID.randomUUID();
            LessonSlot slotA = fixtures.slot(linkA, WEDNESDAY_10H);
            clubABookingId = fixtures.pixBooking(slotA, fixtures.user(STUDENT), BookingStatus.CONFIRMED,
                    PaymentStatus.APPROVED, clubAOrderId).getId();
            clubAFreeSlotId = fixtures.slot(linkA, WEDNESDAY_10H.plus(Duration.ofHours(1))).getId();
            LessonSlot slotB = fixtures.slot(linkB, WEDNESDAY_10H.plus(Duration.ofHours(5)));
            clubBBookingId = fixtures.pixBooking(slotB, fixtures.user(OTHER_STUDENT), BookingStatus.CONFIRMED,
                    PaymentStatus.APPROVED, "ORD-" + UUID.randomUUID()).getId();
            clubAId = clubA.getId();
            clubBId = clubB.getId();
            coachId = coach.getId();
            clubASlotId = slotA.getId();
            clubBSlotId = slotB.getId();
        });
    }

    private static String body(String date, boolean confirm) {
        return "{\"date\":\"%s\",\"confirm\":%s}".formatted(date, confirm);
    }

    private String adminUrl(long club) {
        return "/api/admin/clubs/" + club + "/coaches/" + coachId + "/day-blocks";
    }

    private <T> T inTx(Function<Void, T> read) {
        return QuarkusTransaction.requiringNew().call(() -> read.apply(null));
    }

    private LessonSlotStatus slotStatus(long slotId) {
        return inTx(v -> lessonSlotRepository.findById(slotId).getStatus());
    }

    private BookingStatus bookingStatus(long bookingId) {
        return inTx(v -> bookingRepository.findById(bookingId).getStatus());
    }

    private boolean dayBlocked(Long clubId, String day) {
        return inTx(v -> dayBlockRepository.exists(coachId, clubId, LocalDate.parse(day)));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void adminPreview_listsTheClubsStudentsAndChangesNothing() {
        given().contentType(ContentType.JSON).body(body(DAY, false))
                .when().post(adminUrl(clubAId))
                .then().statusCode(200)
                .body("date", equalTo(DAY))
                .body("applied", equalTo(false))
                .body("affected.size()", equalTo(1))
                .body("affected[0].bookingId", equalTo((int) clubABookingId))
                .body("affected[0].studentName", equalTo(STUDENT))
                .body("affected[0].status", equalTo("CONFIRMED"))
                .body("affected[0].clubName", org.hamcrest.Matchers.startsWith("Bloqueio Clube A"));

        assertEquals(BookingStatus.CONFIRMED, bookingStatus(clubABookingId));
        assertEquals(LessonSlotStatus.OPEN, slotStatus(clubASlotId));
        assertEquals(LessonSlotStatus.OPEN, slotStatus(clubAFreeSlotId));
        assertFalse(dayBlocked(clubAId, DAY));
        verify(gateway, never()).refund(any(), anyString(), anyString());
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void adminConfirm_cancelsAndRefundsTheClubsLessonsAndBlocksItsSlotsWithoutTouchingTheOtherClub() {
        given().contentType(ContentType.JSON).body(body(DAY, true))
                .when().post(adminUrl(clubAId))
                .then().statusCode(200)
                .body("applied", equalTo(true))
                .body("affected.size()", equalTo(1))
                .body("affected[0].status", equalTo("CANCELLED"))
                .body("affected[0].refundStatus", equalTo("DONE"))
                .body("affected[0].refundAmountCents", equalTo(9000));

        verify(gateway).refund(CREDENTIALS, clubAOrderId, "refund-" + clubABookingId);
        verify(gateway, times(1)).refund(any(), anyString(), anyString());
        assertEquals(BookingStatus.CANCELLED, bookingStatus(clubABookingId));
        assertEquals(LessonSlotStatus.BLOCKED, slotStatus(clubASlotId));
        assertEquals(LessonSlotStatus.BLOCKED, slotStatus(clubAFreeSlotId));
        assertEquals(LessonSlotStatus.OPEN, slotStatus(clubBSlotId));
        assertEquals(BookingStatus.CONFIRMED, bookingStatus(clubBBookingId));
        assertEquals(ADMIN, inTx(v -> bookingRepository.findById(clubABookingId).getCancelledBy().getKeycloakId()));
        assertFalse(dayBlocked(null, DAY));
        assertEquals(true, dayBlocked(clubAId, DAY));
        assertFalse(dayBlocked(clubBId, DAY));
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void coachPreviewAndConfirm_coverEveryClubTheCoachWorksAt() {
        given().contentType(ContentType.JSON).body(body(DAY, false))
                .when().post("/api/coach/me/day-blocks")
                .then().statusCode(200)
                .body("applied", equalTo(false))
                .body("affected.bookingId", containsInAnyOrder((int) clubABookingId, (int) clubBBookingId));
        assertEquals(BookingStatus.CONFIRMED, bookingStatus(clubBBookingId));

        given().contentType(ContentType.JSON).body(body(DAY, true))
                .when().post("/api/coach/me/day-blocks")
                .then().statusCode(200)
                .body("applied", equalTo(true))
                .body("affected.status", containsInAnyOrder("CANCELLED", "CANCELLED"));

        verify(gateway, times(2)).refund(any(), anyString(), anyString());
        for (long slotId : new long[]{clubASlotId, clubAFreeSlotId, clubBSlotId}) {
            assertEquals(LessonSlotStatus.BLOCKED, slotStatus(slotId));
        }
        assertEquals(BookingStatus.CANCELLED, bookingStatus(clubBBookingId));
        assertEquals(true, dayBlocked(null, DAY));
        assertEquals(COACH, inTx(v -> bookingRepository.findById(clubBBookingId).getCancelledBy().getKeycloakId()));
    }

    @Test
    @TestSecurity(user = OTHER_ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = OTHER_ADMIN)})
    void adminOfAnotherClub_gets403AndNothingIsBlocked() {
        given().contentType(ContentType.JSON).body(body(DAY, true))
                .when().post(adminUrl(clubAId))
                .then().statusCode(403);

        assertEquals(LessonSlotStatus.OPEN, slotStatus(clubASlotId));
        assertEquals(BookingStatus.CONFIRMED, bookingStatus(clubABookingId));
        assertFalse(dayBlocked(clubAId, DAY));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void adminBlockingACoachOfAnotherClub_returns404() {
        given().contentType(ContentType.JSON).body(body(DAY, true))
                .when().post("/api/admin/clubs/" + clubAId + "/coaches/999999999/day-blocks")
                .then().statusCode(404);
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void userWhoIsNotACoach_gets403() {
        given().contentType(ContentType.JSON).body(body(DAY, true))
                .when().post("/api/coach/me/day-blocks")
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void pastDayIsRejected_andInvalidBodiesReturn400() {
        given().contentType(ContentType.JSON).body(body("2026-10-04", true))
                .when().post("/api/coach/me/day-blocks")
                .then().statusCode(422).body(equalTo("Não é possível bloquear um dia que já passou"));
        given().contentType(ContentType.JSON).body("{\"confirm\":true}")
                .when().post("/api/coach/me/day-blocks").then().statusCode(400);
        given().contentType(ContentType.JSON).body(body("07/10/2026", true))
                .when().post("/api/coach/me/day-blocks").then().statusCode(400);
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void confirmingTwice_isIdempotent() {
        for (int i = 0; i < 2; i++) {
            given().contentType(ContentType.JSON).body(body(DAY, true))
                    .when().post("/api/coach/me/day-blocks").then().statusCode(200).body("applied", equalTo(true));
        }

        verify(gateway, times(2)).refund(any(), anyString(), anyString());
        assertEquals(1L, (long) inTx(v -> dayBlockRepository.count("coach.id = ?1 and day = ?2 and club is null",
                coachId, LocalDate.parse(DAY))));
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void emptyDay_stillBlocksOnConfirmAndListsNobodyOnPreview() {
        String quiet = "2026-10-09";
        given().contentType(ContentType.JSON).body(body(quiet, false))
                .when().post("/api/coach/me/day-blocks")
                .then().statusCode(200).body("affected", empty()).body("applied", equalTo(false));
        assertFalse(dayBlocked(null, quiet));

        given().contentType(ContentType.JSON).body(body(quiet, true))
                .when().post("/api/coach/me/day-blocks")
                .then().statusCode(200).body("affected", empty()).body("applied", equalTo(true));
        assertEquals(true, dayBlocked(null, quiet));
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void heldBooking_isListedAndReleasedWithoutRefundingAnything() {
        String orderId = "ORD-" + UUID.randomUUID();
        long heldId = QuarkusTransaction.requiringNew().call(() -> fixtures.pixBooking(
                lessonSlotRepository.findById(clubAFreeSlotId), fixtures.user(OTHER_STUDENT), BookingStatus.HELD,
                PaymentStatus.PENDING, orderId).getId());

        given().contentType(ContentType.JSON).body(body(DAY, false))
                .when().post("/api/coach/me/day-blocks")
                .then().statusCode(200).body("affected.status", containsInAnyOrder("CONFIRMED", "CONFIRMED", "HELD"));
        given().contentType(ContentType.JSON).body(body(DAY, true))
                .when().post("/api/coach/me/day-blocks").then().statusCode(200);

        assertEquals(BookingStatus.CANCELLED, bookingStatus(heldId));
        verify(gateway).cancel(CREDENTIALS, orderId, "cancel-" + heldId);
        verify(gateway, times(2)).refund(any(), anyString(), anyString());
        assertEquals(PaymentStatus.EXPIRED, inTx(v -> paymentRepository.findByBookingId(heldId).orElseThrow()
                .getStatus()));
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void today_blocksOnlyTheLessonsThatHaveNotStartedYet() {
        Instant started = NOW.minus(Duration.ofHours(1));
        Instant upcoming = NOW.plus(Duration.ofHours(4));
        long[] bookings = QuarkusTransaction.requiringNew().call(() -> new long[]{
                fixtures.studentBooking(fixtures.slot(linkA, started), BookingStatus.CONFIRMED,
                        fixtures.user(STUDENT)).getId(),
                fixtures.studentBooking(fixtures.slot(linkA, upcoming), BookingStatus.CONFIRMED,
                        fixtures.user(STUDENT)).getId()});

        given().contentType(ContentType.JSON).body(body("2026-10-05", true))
                .when().post("/api/coach/me/day-blocks")
                .then().statusCode(200).body("affected.size()", equalTo(1));

        assertEquals(BookingStatus.CONFIRMED, bookingStatus(bookings[0]));
        assertEquals(BookingStatus.CANCELLED, bookingStatus(bookings[1]));
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void guestStudentShowsNameAndPhoneInThePreview() {
        QuarkusTransaction.requiringNew().run(() -> {
            Booking guest = bookingRepository.findById(clubABookingId);
            guest.setStudentUser(null);
            guest.setGuestName("Ana Convidada");
            guest.setGuestPhone("98999990000");
        });
        given().contentType(ContentType.JSON).body(body(DAY, false))
                .when().post("/api/coach/me/day-blocks")
                .then().statusCode(200)
                .body("affected.find { it.bookingId == " + clubABookingId + " }.studentName", equalTo("Ana Convidada"))
                .body("affected.find { it.bookingId == " + clubABookingId + " }.studentPhone", equalTo("98999990000"));
        given().contentType(ContentType.JSON).body(body(DAY, true))
                .when().post("/api/coach/me/day-blocks").then().statusCode(200);
    }
}
