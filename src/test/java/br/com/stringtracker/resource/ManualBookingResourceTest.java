package br.com.stringtracker.resource;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlotStatus;
import br.com.stringtracker.model.schedule.PaymentMode;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.LessonSlotRepository;
import br.com.stringtracker.service.ClockProducer;
import br.com.stringtracker.service.payment.PaymentGateway;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@QuarkusTest
class ManualBookingResourceTest {

    private static final String ADMIN = "kc-manual-admin";
    private static final String OTHER_ADMIN = "kc-manual-other-admin";
    private static final String COACH = "kc-manual-coach";
    private static final String OTHER_COACH = "kc-manual-other-coach";
    private static final String STUDENT = "kc-manual-student";
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final String GUEST = "\"guestName\":\"Ana\",\"guestPhone\":\"98999990000\"";

    @InjectMock
    PaymentGateway gateway;

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    LessonSlotRepository lessonSlotRepository;

    private long clubId;
    private ClubCoach link;
    private ClubCoach otherCoachLink;

    /** COACH e OTHER_COACH atendem no mesmo clube (singles R$ 90, duplas R$ 120); ADMIN administra esse clube. */
    @BeforeEach
    void seed() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
        QuarkusTransaction.requiringNew().run(() -> {
            Club club = fixtures.club("Manual Clube");
            fixtures.admin(club, fixtures.user(ADMIN));
            fixtures.admin(fixtures.club("Manual Outro"), fixtures.user(OTHER_ADMIN));
            fixtures.user(STUDENT);
            link = fixtures.link(club, fixtures.coach(fixtures.user(COACH)), 9000L, 12000L);
            otherCoachLink = fixtures.link(club, fixtures.coach(fixtures.user(OTHER_COACH)), 9000L, 12000L);
            clubId = club.getId();
        });
    }

    private static String body(long slotId, String type, String extra) {
        return "{\"slotId\":%d,\"type\":\"%s\"%s}".formatted(slotId, type, extra == null ? "" : "," + extra);
    }

    private long freeSlot(ClubCoach of, Duration startsIn) {
        return QuarkusTransaction.requiringNew().call(() -> fixtures.slot(of, NOW.plus(startsIn)).getId());
    }

    private <T> T inBooking(long bookingId, Function<Booking, T> read) {
        return QuarkusTransaction.requiringNew().call(() -> read.apply(bookingRepository.findById(bookingId)));
    }

    private long bookingsAt(long slotId) {
        return QuarkusTransaction.requiringNew().call(() -> bookingRepository.count("lessonSlot.id", slotId));
    }

    private String adminUrl() {
        return "/api/admin/clubs/" + clubId + "/bookings";
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void adminBooksAStudentWithAnAccount_confirmedPaidOutsideAndLinkedToThem() {
        long slotId = freeSlot(link, Duration.ofDays(2));
        long studentId = QuarkusTransaction.requiringNew().call(() -> fixtures.user(STUDENT).getId());

        long bookingId = given().contentType(ContentType.JSON)
                .body(body(slotId, "SINGLES", "\"studentUserId\":" + studentId))
                .when().post(adminUrl())
                .then().statusCode(201)
                .body("status", equalTo("CONFIRMED"))
                .body("paymentMode", equalTo("OFFLINE"))
                .body("priceCents", equalTo(9000))
                .extract().jsonPath().getLong("bookingId");

        inBooking(bookingId, booking -> {
            assertEquals(STUDENT, booking.getStudentUser().getKeycloakId());
            assertEquals(ADMIN, booking.getCreatedBy().getKeycloakId());
            assertEquals(PaymentMode.OFFLINE, booking.getPaymentMode());
            assertNull(booking.getGuestName());
            return null;
        });
        verify(gateway, never()).createPix(any(), anyLong(), anyString(), anyString(), anyString(), any(),
                anyString());
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void adminBooksAGuest_keepsTheNameAndOnlyTheDigitsOfThePhone_andDoublesKeepThePartner() {
        long slotId = freeSlot(link, Duration.ofDays(2));

        long bookingId = given().contentType(ContentType.JSON)
                .body(body(slotId, "DOUBLES",
                        "\"guestName\":\" Ana Souza \",\"guestPhone\":\"(98) 99999-0000\",\"partnerName\":\"Bia\""))
                .when().post(adminUrl())
                .then().statusCode(201)
                .body("priceCents", equalTo(12000))
                .body("partnerName", equalTo("Bia"))
                .extract().jsonPath().getLong("bookingId");

        inBooking(bookingId, booking -> {
            assertEquals("Ana Souza", booking.getGuestName());
            assertEquals("98999990000", booking.getGuestPhone());
            assertNull(booking.getStudentUser());
            return null;
        });
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void invalidStudentData_returns400OrNotFound_andBooksNothing() {
        long slotId = freeSlot(link, Duration.ofDays(2));
        long studentId = QuarkusTransaction.requiringNew().call(() -> fixtures.user(STUDENT).getId());

        for (String extra : new String[]{"\"guestName\":\"Ana\"", "\"guestPhone\":\"98999990000\"", null,
                "\"guestName\":\"   \",\"guestPhone\":\"98999990000\"",
                "\"studentUserId\":%d,\"guestName\":\"Ana\"".formatted(studentId)}) {
            given().contentType(ContentType.JSON).body(body(slotId, "SINGLES", extra))
                    .when().post(adminUrl()).then().statusCode(400);
        }
        given().contentType(ContentType.JSON).body(body(slotId, "SINGLES", "\"guestName\":\"Ana\",\"guestPhone\":\"123\""))
                .when().post(adminUrl()).then().statusCode(400).body(equalTo("Telefone inválido"));
        given().contentType(ContentType.JSON).body(body(slotId, "SINGLES", "\"studentUserId\":999999999"))
                .when().post(adminUrl()).then().statusCode(404);

        assertEquals(0L, bookingsAt(slotId));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void slotThatIsHeldConfirmedOrBlocked_returns409() {
        long confirmed = freeSlot(link, Duration.ofDays(2));
        long held = freeSlot(link, Duration.ofDays(3));
        long blocked = freeSlot(link, Duration.ofDays(4));
        QuarkusTransaction.requiringNew().run(() -> {
            fixtures.booking(lessonSlotRepository.findById(confirmed), BookingStatus.CONFIRMED, fixtures.user(ADMIN));
            fixtures.pixBooking(lessonSlotRepository.findById(held), fixtures.user(STUDENT), BookingStatus.HELD,
                    PaymentStatus.PENDING, "ORD-" + UUID.randomUUID());
            lessonSlotRepository.findById(blocked).setStatus(LessonSlotStatus.BLOCKED);
        });

        for (long slotId : new long[]{confirmed, held, blocked}) {
            given().contentType(ContentType.JSON).body(body(slotId, "SINGLES", GUEST))
                    .when().post(adminUrl())
                    .then().statusCode(409).body(equalTo("Esse horário não está mais livre"));
        }
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void slotOfAnotherClubTypeNotOfferedStartedOrUnknown_areRejected() {
        long otherClubSlot = QuarkusTransaction.requiringNew().call(() -> {
            ClubCoach elsewhere = fixtures.link(fixtures.club("Manual Terceiro"), fixtures.coach(), 9000L, null);
            return fixtures.slot(elsewhere, NOW.plus(Duration.ofDays(2))).getId();
        });
        long started = freeSlot(link, Duration.ofMinutes(-30));
        long upcoming = freeSlot(link, Duration.ofDays(2));

        given().contentType(ContentType.JSON).body(body(otherClubSlot, "SINGLES", GUEST))
                .when().post(adminUrl()).then().statusCode(403);
        given().contentType(ContentType.JSON).body(body(upcoming, "GROUP", GUEST))
                .when().post(adminUrl()).then().statusCode(422)
                .body(equalTo("O professor não oferece esse tipo de aula neste horário"));
        given().contentType(ContentType.JSON).body(body(started, "SINGLES", GUEST))
                .when().post(adminUrl()).then().statusCode(422).body(equalTo("A aula já começou"));
        given().contentType(ContentType.JSON).body(body(999999999L, "SINGLES", GUEST))
                .when().post(adminUrl()).then().statusCode(404);

        assertEquals(0L, bookingsAt(otherClubSlot));
    }

    @Test
    @TestSecurity(user = OTHER_ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = OTHER_ADMIN)})
    void adminOfAnotherClub_cannotBookManually() {
        long slotId = freeSlot(link, Duration.ofDays(2));

        given().contentType(ContentType.JSON).body(body(slotId, "SINGLES", GUEST))
                .when().post(adminUrl()).then().statusCode(403);

        assertEquals(0L, bookingsAt(slotId));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void twoSimultaneousManualBookings_oneGets201AndTheOtherGets409() throws Exception {
        long slotId = freeSlot(link, Duration.ofDays(2));
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    return given().contentType(ContentType.JSON).body(body(slotId, "SINGLES", GUEST))
                            .when().post(adminUrl()).statusCode();
                }));
            }
            go.countDown();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
        } finally {
            pool.shutdownNow();
        }

        assertTrue(statuses.contains(201) && statuses.contains(409), "statuses: " + statuses);
        assertEquals(1L, bookingsAt(slotId));
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void coachBooksAFreeSlotOfTheirOwnAgenda() {
        long slotId = freeSlot(link, Duration.ofDays(2));

        long bookingId = given().contentType(ContentType.JSON).body(body(slotId, "SINGLES", GUEST))
                .when().post("/api/coach/me/bookings")
                .then().statusCode(201)
                .body("status", equalTo("CONFIRMED"))
                .body("paymentMode", equalTo("OFFLINE"))
                .extract().jsonPath().getLong("bookingId");

        inBooking(bookingId, booking -> {
            assertEquals(COACH, booking.getCreatedBy().getKeycloakId());
            assertEquals("Ana", booking.getGuestName());
            return null;
        });
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void coachCannotBookSlotsOfAnotherCoachOrAnOccupiedOne() {
        long others = freeSlot(otherCoachLink, Duration.ofDays(2));
        long occupied = freeSlot(link, Duration.ofDays(3));
        QuarkusTransaction.requiringNew().run(() -> fixtures.booking(lessonSlotRepository.findById(occupied),
                BookingStatus.CONFIRMED, fixtures.user(COACH)));

        given().contentType(ContentType.JSON).body(body(others, "SINGLES", GUEST))
                .when().post("/api/coach/me/bookings").then().statusCode(403);
        given().contentType(ContentType.JSON).body(body(occupied, "SINGLES", GUEST))
                .when().post("/api/coach/me/bookings").then().statusCode(409)
                .body(equalTo("Esse horário não está mais livre"));

        assertEquals(0L, bookingsAt(others));
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void userWhoIsNotACoach_cannotBookThroughTheCoachRoute() {
        long slotId = freeSlot(link, Duration.ofDays(2));

        given().contentType(ContentType.JSON).body(body(slotId, "SINGLES", GUEST))
                .when().post("/api/coach/me/bookings").then().statusCode(403);

        assertEquals(0L, bookingsAt(slotId));
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void studentSeesTheManualBookingLinkedToThem_withThePaymentMode() {
        long slotId = freeSlot(link, Duration.ofDays(2));
        long bookingId = QuarkusTransaction.requiringNew().call(() -> fixtures.studentBooking(
                lessonSlotRepository.findById(slotId), BookingStatus.CONFIRMED, fixtures.user(STUDENT)).getId());

        given().when().get("/api/lessons/bookings/" + bookingId)
                .then().statusCode(200).body("status", equalTo("CONFIRMED")).body("paymentMode", equalTo("OFFLINE"));
    }
}
