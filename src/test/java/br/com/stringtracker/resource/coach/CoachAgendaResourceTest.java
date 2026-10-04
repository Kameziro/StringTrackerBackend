package br.com.stringtracker.resource.coach;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.User;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonSlotStatus;
import br.com.stringtracker.model.schedule.LessonType;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.LessonSlotRepository;
import br.com.stringtracker.service.ClockProducer;
import br.com.stringtracker.support.ScheduleFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

@QuarkusTest
class CoachAgendaResourceTest {

    private static final String COACH = "kc-agenda-coach";
    private static final String OTHER_COACH = "kc-agenda-other-coach";
    private static final String STUDENT = "kc-agenda-student";

    /** Segunda-feira 2026-10-05, 09:00 em São Paulo. */
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final Instant WEDNESDAY_10H = Instant.parse("2026-10-07T13:00:00Z");

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    LessonSlotRepository lessonSlotRepository;

    private ClubCoach linkA;
    private ClubCoach linkB;
    private ClubCoach otherCoachLink;

    /** COACH atende nos clubes A e B; OTHER_COACH atende no A. */
    @BeforeEach
    void seed() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
        QuarkusTransaction.requiringNew().run(() -> {
            Club clubA = fixtures.club("Agenda Prof A");
            Club clubB = fixtures.club("Agenda Prof B");
            fixtures.user(STUDENT);
            User coachUser = fixtures.user(COACH);
            linkA = fixtures.link(clubA, fixtures.coach(coachUser), 9000L, 12000L);
            linkB = fixtures.link(clubB, fixtures.coach(coachUser), 9000L, 12000L);
            otherCoachLink = fixtures.link(clubA, fixtures.coach(fixtures.user(OTHER_COACH)), 9000L, 12000L);
        });
    }

    private long slot(ClubCoach link, Duration afterWednesday10h) {
        return QuarkusTransaction.requiringNew().call(() ->
                fixtures.slot(link, WEDNESDAY_10H.plus(afterWednesday10h)).getId());
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void listsTheLessonsOfEveryClubWithTheClubNameStudentAndPaymentStatus() {
        long paidSlot = slot(linkA, Duration.ZERO);
        long heldSlot = slot(linkB, Duration.ofHours(3));
        long freeSlot = slot(linkA, Duration.ofHours(6));
        QuarkusTransaction.requiringNew().run(() -> {
            LessonSlot paid = lessonSlotRepository.findById(paidSlot);
            fixtures.pixBooking(paid, fixtures.user(STUDENT), BookingStatus.CONFIRMED, PaymentStatus.APPROVED,
                    "ORD-paid");
            LessonSlot held = lessonSlotRepository.findById(heldSlot);
            fixtures.pixBooking(held, fixtures.user(STUDENT), BookingStatus.HELD, PaymentStatus.PENDING, "ORD-held");
        });

        given().queryParam("from", "2026-10-05").queryParam("to", "2026-10-11").when().get("/api/coach/me/agenda")
                .then().statusCode(200)
                .body("from", equalTo("2026-10-05"))
                .body("to", equalTo("2026-10-11"))
                .body("slots.slotId", contains((int) paidSlot, (int) heldSlot, (int) freeSlot))
                .body("slots[0].clubName", startsWith("Agenda Prof A"))
                .body("slots[1].clubName", startsWith("Agenda Prof B"))
                .body("slots[0].status", equalTo("BOOKED"))
                .body("slots[0].lessons", hasSize(1))
                .body("slots[0].lessons[0].studentName", equalTo(STUDENT))
                .body("slots[0].lessons[0].paymentMode", equalTo("PIX"))
                .body("slots[0].lessons[0].paymentStatus", equalTo("APPROVED"))
                .body("slots[1].status", equalTo("HELD"))
                .body("slots[1].lessons[0].paymentStatus", equalTo("PENDING"))
                .body("slots[2].status", equalTo("FREE"))
                .body("slots[2].lessons", empty());
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void guestWithoutAccountShowsNameAndPhone_manualBookingIsPaidOutside_andDoublesShowThePartner() {
        long guestSlot = slot(linkA, Duration.ZERO);
        long doublesSlot = slot(linkB, Duration.ofHours(3));
        QuarkusTransaction.requiringNew().run(() -> {
            Booking guest = fixtures.booking(lessonSlotRepository.findById(guestSlot),
                    BookingStatus.CONFIRMED, fixtures.user(COACH));
            guest.setGuestName("Ana Convidada");
            guest.setGuestPhone("98999990000");
            Booking doubles = fixtures.pixBooking(
                    lessonSlotRepository.findById(doublesSlot), fixtures.user(STUDENT),
                    BookingStatus.CONFIRMED, PaymentStatus.APPROVED, "ORD-doubles");
            doubles.setLessonType(LessonType.DOUBLES);
            doubles.setPartnerName("Bia");
        });

        given().queryParam("from", "2026-10-07").queryParam("to", "2026-10-07").when().get("/api/coach/me/agenda")
                .then().statusCode(200)
                .body("slots[0].lessons[0].studentName", equalTo("Ana Convidada"))
                .body("slots[0].lessons[0].studentPhone", equalTo("98999990000"))
                .body("slots[0].lessons[0].studentUserId", nullValue())
                .body("slots[0].lessons[0].paymentMode", equalTo("OFFLINE"))
                .body("slots[0].lessons[0].paymentStatus", nullValue())
                .body("slots[1].lessons[0].lessonType", equalTo("DOUBLES"))
                .body("slots[1].lessons[0].partnerName", equalTo("Bia"))
                .body("slots[1].lessons[0].studentPhone", nullValue());
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void onlyTheRequestedPeriodAppears_withTheLastDayInclusive_andNoDatesMeanTheWeekFromToday() {
        long monday = slot(linkA, Duration.ofDays(-2));
        long sunday = slot(linkA, Duration.ofDays(4));
        long nextMonday = slot(linkA, Duration.ofDays(5));

        given().queryParam("from", "2026-10-05").queryParam("to", "2026-10-11").when().get("/api/coach/me/agenda")
                .then().statusCode(200).body("slots.slotId", contains((int) monday, (int) sunday));
        given().when().get("/api/coach/me/agenda")
                .then().statusCode(200)
                .body("from", equalTo("2026-10-05"))
                .body("to", equalTo("2026-10-11"))
                .body("slots.slotId", contains((int) monday, (int) sunday));
        given().queryParam("from", "2026-10-12").queryParam("to", "2026-10-12").when().get("/api/coach/me/agenda")
                .then().statusCode(200).body("slots.slotId", contains((int) nextMonday));
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void blockedSlotsAppear_butRemovedOnesCancelledBookingsAndOtherCoachesDoNot() {
        long blocked = slot(linkA, Duration.ZERO);
        long removed = slot(linkA, Duration.ofHours(3));
        long cancelled = slot(linkA, Duration.ofHours(6));
        long others = slot(otherCoachLink, Duration.ofHours(1));
        QuarkusTransaction.requiringNew().run(() -> {
            lessonSlotRepository.findById(blocked).setStatus(LessonSlotStatus.BLOCKED);
            lessonSlotRepository.findById(removed).setStatus(LessonSlotStatus.REMOVED);
            fixtures.studentBooking(lessonSlotRepository.findById(cancelled),
                    BookingStatus.CANCELLED, fixtures.user(STUDENT));
            fixtures.studentBooking(lessonSlotRepository.findById(others),
                    BookingStatus.CONFIRMED, fixtures.user(STUDENT));
        });

        given().queryParam("from", "2026-10-07").queryParam("to", "2026-10-07").when().get("/api/coach/me/agenda")
                .then().statusCode(200)
                .body("slots.slotId", contains((int) blocked, (int) cancelled))
                .body("slots[0].status", equalTo("BLOCKED"))
                .body("slots[1].status", equalTo("FREE"))
                .body("slots[1].lessons", empty());
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void userWhoIsNotACoach_gets403() {
        given().when().get("/api/coach/me/agenda").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void invalidPeriods_return400() {
        given().queryParam("from", "05/10/2026").when().get("/api/coach/me/agenda").then().statusCode(400);
        given().queryParam("from", "2026-10-10").queryParam("to", "2026-10-09").when().get("/api/coach/me/agenda")
                .then().statusCode(400);
        given().queryParam("from", "2026-10-01").queryParam("to", "2026-12-31").when().get("/api/coach/me/agenda")
                .then().statusCode(400);
    }
}
