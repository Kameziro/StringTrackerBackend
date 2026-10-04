package br.com.stringtracker.resource.coach;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.ClubCoachRepository;
import br.com.stringtracker.support.ScheduleFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class CoachMeResourceTest {

    private static final String COACH = "kc-coachme-coach";
    private static final String COMMON = "kc-coachme-common";

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    ClubCoachRepository clubCoachRepository;

    private long coachId;
    private long clubAId;
    private long clubBId;
    private ClubCoach linkB;

    /**
     * O professor atende no clube A (singles e duplas com preço) e no B (só singles com preço); tem ainda um vínculo
     * desativado e um vínculo com clube desativado, que não aparecem. Os clubes têm nomes ordenados A, B.
     */
    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            User user = fixtures.user(COACH);
            fixtures.user(COMMON);
            Coach coach = fixtures.coach(user);
            clubCoachRepository.update("active = false where coach.id = ?1", coach.getId());

            Club clubA = fixtures.club("Eu Clube A");
            Club clubB = fixtures.club("Eu Clube B");
            Club clubGone = fixtures.club("Eu Clube Desativado");
            Club clubUnlinked = fixtures.club("Eu Clube Desvinculado");
            fixtures.link(clubA, coach, 9000L, 12000L);
            linkB = fixtures.link(clubB, coach, 8000L, null);
            fixtures.link(clubGone, coach, 7000L, null);
            fixtures.link(clubUnlinked, coach, 7000L, null).markExcluded();
            clubGone.markExcluded();
            coachId = coach.getId();
            clubAId = clubA.getId();
            clubBId = clubB.getId();
        });
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void coach_readsTheirOfferedTypesAndTheirActiveClubsWithTheClubsPrices() {
        given().when().get("/api/coach/me")
                .then().statusCode(200)
                .body("coachId", equalTo((int) coachId))
                .body("name", equalTo(COACH))
                .body("avatarUrl", nullValue())
                .body("bio", nullValue())
                .body("offers.singles", equalTo(true))
                .body("offers.doubles", equalTo(true))
                .body("offers.group", equalTo(false))
                .body("clubs.clubId", contains((int) clubAId, (int) clubBId))
                .body("clubs[0].prices.clubId", equalTo((int) clubAId))
                .body("clubs[0].prices.coachId", equalTo((int) coachId))
                .body("clubs[0].prices.singlesCents", equalTo(9000))
                .body("clubs[0].prices.doublesCents", equalTo(12000))
                .body("clubs[0].prices.groupCents", nullValue())
                .body("clubs[1].prices.singlesCents", equalTo(8000))
                .body("clubs[1].prices.doublesCents", nullValue())
                .body("clubs[0].logoUrl", nullValue());
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void read_reflectsTheOffersSavedByThePut_andPricesChangedByTheClub() {
        given().contentType(ContentType.JSON).body("{\"singles\":false,\"doubles\":true,\"group\":true}")
                .when().put("/api/coach/me/offers").then().statusCode(200);
        QuarkusTransaction.requiringNew().run(() -> {
            ClubCoach link = clubCoachRepository.findById(linkB.getId());
            link.setPriceDoublesCents(11000L);
        });

        given().when().get("/api/coach/me")
                .then().statusCode(200)
                .body("offers.singles", equalTo(false))
                .body("offers.doubles", equalTo(true))
                .body("offers.group", equalTo(true))
                .body("clubs[1].prices.doublesCents", equalTo(11000));
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void coachWithoutActiveClubs_getsAnEmptyList() {
        QuarkusTransaction.requiringNew().run(() ->
                clubCoachRepository.update("active = false where coach.id = ?1", coachId));

        given().when().get("/api/coach/me")
                .then().statusCode(200)
                .body("clubs", empty());
    }

    @Test
    @TestSecurity(user = COMMON)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COMMON)})
    void someoneWhoIsNotACoach_returns403() {
        given().when().get("/api/coach/me").then().statusCode(403);
    }

    @Test
    void withoutAToken_returns401() {
        given().when().get("/api/coach/me").then().statusCode(401);
    }
}
