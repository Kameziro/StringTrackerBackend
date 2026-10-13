package br.com.stringtracker.resource;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.User;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.repository.ClubCoachRepository;
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
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class LessonCoachResourceTest {

    private static final String STUDENT = "kc-coachpage-student";
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    ClubCoachRepository clubCoachRepository;

    @Inject
    LessonSlotRepository lessonSlotRepository;

    private long coachId;
    private long clubAId;
    private long clubBId;
    private long slotBId;
    private long linkAId;
    private long linkBId;

    /**
     * O professor atende nos clubes A (Pix conectado; singles R$ 90 e duplas R$ 120) e B (sem Pix; só singles a
     * R$ 100). No A: dois horários livres e um já à frente de 2h. No B: um horário livre.
     */
    @BeforeEach
    void seed() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
        QuarkusTransaction.requiringNew().run(() -> {
            fixtures.user(STUDENT);
            User coachUser = fixtures.user("kc-coachpage-coach-" + UUID.randomUUID());
            coachUser.setName("Professor Pagina");
            Coach coach = fixtures.coach(coachUser);
            coach.setBio("Treino de bandeja e vibora");
            Club clubA = fixtures.connectPayments(fixtures.club("Pagina Prof A"), NOW.plus(Duration.ofDays(30)));
            Club clubB = fixtures.club("Pagina Prof B");
            ClubCoach linkA = fixtures.link(clubA, coach, 9000L, 12000L);
            ClubCoach linkB = fixtures.link(clubB, coach, 10000L, null);
            linkAId = linkA.getId();
            linkBId = linkB.getId();
            fixtures.slot(linkA, NOW.plus(Duration.ofDays(1)));
            fixtures.slot(linkA, NOW.plus(Duration.ofDays(1)).plus(Duration.ofHours(2)));
            fixtures.slot(linkA, NOW.plus(Duration.ofMinutes(90)));
            slotBId = fixtures.slot(linkB, NOW.plus(Duration.ofDays(2))).getId();
            coachId = coach.getId();
            clubAId = clubA.getId();
            clubBId = clubB.getId();
        });
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void coachInTwoClubs_listsBothWithTheirOwnTypesPricesAndBookableSlots() {
        given().when().get("/api/lessons/coaches/" + coachId)
                .then().statusCode(200)
                .body("coachId", equalTo((int) coachId))
                .body("name", equalTo("Professor Pagina"))
                .body("bio", equalTo("Treino de bandeja e vibora"))
                .body("clubs", hasSize(2))
                .body("clubs.find { it.clubId == " + clubAId + " }.acceptsPix", equalTo(true))
                .body("clubs.find { it.clubId == " + clubAId + " }.types.type", contains("SINGLES", "DOUBLES"))
                .body("clubs.find { it.clubId == " + clubAId + " }.types.priceCents", contains(9000, 12000))
                .body("clubs.find { it.clubId == " + clubAId + " }.slots.startsAt",
                        contains("2026-10-06T12:00:00Z", "2026-10-06T14:00:00Z"))
                .body("clubs.find { it.clubId == " + clubBId + " }.acceptsPix", equalTo(false))
                .body("clubs.find { it.clubId == " + clubBId + " }.types.type", contains("SINGLES"))
                .body("clubs.find { it.clubId == " + clubBId + " }.types.priceCents", contains(10000))
                .body("clubs.find { it.clubId == " + clubBId + " }.slots", hasSize(1));
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void clubIdFilter_keepsOnlyThatClub() {
        given().queryParam("clubId", clubBId).when().get("/api/lessons/coaches/" + coachId)
                .then().statusCode(200)
                .body("clubs", hasSize(1))
                .body("clubs[0].clubId", equalTo((int) clubBId))
                .body("clubs[0].slots", hasSize(1));
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void aSlotThatGotBooked_leavesTheClubListedWithoutSlots() {
        QuarkusTransaction.requiringNew().run(() -> fixtures.studentBooking(
                lessonSlotRepository.findById(slotBId), BookingStatus.CONFIRMED, fixtures.user(STUDENT)));

        given().queryParam("clubId", clubBId).when().get("/api/lessons/coaches/" + coachId)
                .then().statusCode(200).body("clubs[0].slots", empty());
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void coachWithoutAnyActiveLink_orFilteredByAClubTheyDoNotWorkAt_returns404WithTheLinkUnavailableCode() {
        long elsewhere = QuarkusTransaction.requiringNew().call(() -> fixtures.club("Pagina Prof C").getId());

        given().queryParam("clubId", elsewhere).when().get("/api/lessons/coaches/" + coachId)
                .then().statusCode(404).body("code", equalTo("LINK_UNAVAILABLE"));

        QuarkusTransaction.requiringNew().run(() -> {
            clubCoachRepository.findById(linkAId).setActive(false);
            clubCoachRepository.findById(linkBId).setActive(false);
        });
        given().when().get("/api/lessons/coaches/" + coachId)
                .then().statusCode(404)
                .body("code", equalTo("LINK_UNAVAILABLE"))
                .body("message", equalTo("Este link não está mais disponível"));
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void linkOfAnInactiveClubIsHidden_andAnUnknownCoachReturns404() {
        QuarkusTransaction.requiringNew().run(() -> clubCoachRepository.findById(linkBId).getClub().setActive(false));

        given().when().get("/api/lessons/coaches/" + coachId)
                .then().statusCode(200).body("clubs.clubId", contains((int) clubAId));
        given().when().get("/api/lessons/coaches/999999999").then().statusCode(404);
    }

    @Test
    void withoutLogin_returns401() {
        given().when().get("/api/lessons/coaches/" + coachId).then().statusCode(401);
    }
}
