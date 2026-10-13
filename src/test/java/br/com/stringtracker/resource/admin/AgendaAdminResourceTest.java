package br.com.stringtracker.resource.admin;

import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.User;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonSlotStatus;
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
import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class AgendaAdminResourceTest {

    private static final String ADMIN = "kc-agenda-admin";
    private static final String OTHER_ADMIN = "kc-agenda-other-admin";
    private static final String COMMON = "kc-agenda-common";

    /** Semana de segunda 2026-10-05 a domingo 2026-10-11 (UTC-3, sem horário de verão). */
    private static final String MONDAY = "2026-10-05";

    @Inject
    ScheduleFixtures fixtures;

    private long clubId;
    private long coachId;
    private long idleCoachId;
    private long otherClubCoachId;
    private LessonSlot free;
    private LessonSlot held;
    private LessonSlot booked;
    private LessonSlot blocked;
    private LessonSlot cancelledOnly;

    /** Clube com dois professores (um sem horários); um professor de outro clube também tem horários na semana. */
    @BeforeEach
    void seed() {
        QuarkusMock.installMockForType(Clock.fixed(Instant.parse("2026-10-07T15:00:00Z"), ClockProducer.ZONE),
                Clock.class);
        QuarkusTransaction.requiringNew().run(() -> {
            User admin = fixtures.user(ADMIN);
            fixtures.user(COMMON);
            var club = fixtures.club("Agenda Semanal");
            var otherClub = fixtures.club("Agenda Outro");
            fixtures.admin(club, admin);
            fixtures.admin(otherClub, fixtures.user(OTHER_ADMIN));
            Coach coach = fixtures.coach();
            Coach idle = fixtures.coach();
            Coach elsewhere = fixtures.coach();
            ClubCoach link = fixtures.link(club, coach, 10000L, 15000L);
            fixtures.link(club, idle, 10000L, null);
            ClubCoach otherLink = fixtures.link(otherClub, elsewhere, 10000L, null);

            // Datas em UTC: 15h UTC = 12h em São Paulo, todas dentro da semana de 05 a 11/10.
            free = fixtures.slot(link, Instant.parse("2026-10-06T15:00:00Z"));
            held = fixtures.slot(link, Instant.parse("2026-10-06T17:00:00Z"));
            booked = fixtures.slot(link, Instant.parse("2026-10-07T15:00:00Z"));
            blocked = fixtures.slot(link, Instant.parse("2026-10-08T15:00:00Z"));
            blocked.setStatus(LessonSlotStatus.BLOCKED);
            cancelledOnly = fixtures.slot(link, Instant.parse("2026-10-09T15:00:00Z"));
            LessonSlot removed = fixtures.slot(link, Instant.parse("2026-10-10T15:00:00Z"));
            removed.setStatus(LessonSlotStatus.REMOVED);
            fixtures.slot(link, Instant.parse("2026-10-12T15:00:00Z"));   // semana seguinte
            fixtures.slot(otherLink, Instant.parse("2026-10-06T15:00:00Z")); // outro clube

            fixtures.booking(held, BookingStatus.HELD, admin);
            fixtures.booking(booked, BookingStatus.CONFIRMED, admin);
            fixtures.booking(cancelledOnly, BookingStatus.CANCELLED, admin);
            fixtures.booking(cancelledOnly, BookingStatus.EXPIRED, admin);
            clubId = club.getId();
            coachId = coach.getId();
            idleCoachId = idle.getId();
            otherClubCoachId = elsewhere.getId();
        });
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void weekGrid_showsFreeHeldBookedAndBlockedSlots_withoutRemovedOtherWeeksOrOtherClubs() {
        given().queryParam("week", MONDAY)
                .when().get("/api/admin/clubs/%d/agenda".formatted(clubId))
                .then().statusCode(200)
                .body("weekStart", equalTo("2026-10-05"))
                .body("weekEnd", equalTo("2026-10-11"))
                .body("slots", hasSize(5))
                .body("slots.id", contains((int) free.getId().longValue(), (int) held.getId().longValue(),
                        (int) booked.getId().longValue(), (int) blocked.getId().longValue(),
                        (int) cancelledOnly.getId().longValue()))
                .body("slots.status", contains("FREE", "HELD", "BOOKED", "BLOCKED", "FREE"))
                .body("slots.coachId", contains((int) coachId, (int) coachId, (int) coachId, (int) coachId,
                        (int) coachId))
                .body("slots[0].startsAt", equalTo("2026-10-06T15:00:00Z"))
                .body("slots[0].endsAt", equalTo("2026-10-06T16:00:00Z"))
                .body("slots[0].kind", equalTo("PRIVATE"))
                .body("slots[0].capacity", equalTo(1));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void weekGrid_listsAllCoachesOfTheClub_includingThoseWithoutSlots() {
        given().queryParam("week", MONDAY)
                .when().get("/api/admin/clubs/%d/agenda".formatted(clubId))
                .then().statusCode(200)
                .body("coaches", hasSize(2))
                .body("coaches.coachId", containsInAnyOrder((int) coachId, (int) idleCoachId))
                .body("coaches.coachId", not(hasItem((int) otherClubCoachId)));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void anyDayOfTheWeekSelectsThatWeek_andWithoutTheParameterTodaysWeekIsUsed() {
        // Domingo 11/10 ainda é a semana de 05/10.
        given().queryParam("week", "2026-10-11")
                .when().get("/api/admin/clubs/%d/agenda".formatted(clubId))
                .then().statusCode(200)
                .body("weekStart", equalTo("2026-10-05"))
                .body("slots", hasSize(5));

        // O relógio do teste marca quarta 07/10: sem parâmetro vale a semana de 05/10.
        given().when().get("/api/admin/clubs/%d/agenda".formatted(clubId))
                .then().statusCode(200)
                .body("weekStart", equalTo("2026-10-05"))
                .body("slots", hasSize(5));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void weekWithoutSlots_returnsAnEmptySlotList() {
        given().queryParam("week", "2026-12-07")
                .when().get("/api/admin/clubs/%d/agenda".formatted(clubId))
                .then().statusCode(200)
                .body("slots", empty())
                .body("coaches", hasSize(2));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void invalidWeek_returns400() {
        given().queryParam("week", "semana-que-vem")
                .when().get("/api/admin/clubs/%d/agenda".formatted(clubId))
                .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = OTHER_ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = OTHER_ADMIN)})
    void adminOfAnotherClub_returns403() {
        given().queryParam("week", MONDAY)
                .when().get("/api/admin/clubs/%d/agenda".formatted(clubId))
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = COMMON)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COMMON)})
    void commonUser_returns403() {
        given().queryParam("week", MONDAY)
                .when().get("/api/admin/clubs/%d/agenda".formatted(clubId))
                .then().statusCode(403);
    }
}
