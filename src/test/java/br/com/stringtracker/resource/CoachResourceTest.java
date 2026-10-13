package br.com.stringtracker.resource;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubAdmin;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.ClubAdminRepository;
import br.com.stringtracker.repository.ClubCoachRepository;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.repository.CoachRepository;
import br.com.stringtracker.repository.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class CoachResourceTest {

    private static final String COACH = "kc-coachres-coach";
    private static final String ADMIN_A = "kc-coachres-admin-a";
    private static final String ADMIN_B = "kc-coachres-admin-b";
    private static final String COMMON = "kc-coachres-common";

    @Inject
    UserRepository userRepository;

    @Inject
    ClubRepository clubRepository;

    @Inject
    ClubAdminRepository clubAdminRepository;

    @Inject
    CoachRepository coachRepository;

    @Inject
    ClubCoachRepository clubCoachRepository;

    private long clubAId;
    private long clubBId;
    private long clubCId;
    private long coachId;

    /** O professor atende nos clubes A e B; o clube C (admin B) não o tem. */
    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            User coachUser = user(COACH);
            User adminA = user(ADMIN_A);
            User adminB = user(ADMIN_B);
            user(COMMON);
            Club clubA = club("Professor Clube A");
            Club clubB = club("Professor Clube B");
            Club clubC = club("Professor Clube C");
            clubAdminRepository.persist(ClubAdmin.create(clubA, adminA));
            clubAdminRepository.persist(ClubAdmin.create(clubB, adminB));
            clubAdminRepository.persist(ClubAdmin.create(clubC, adminB));

            Coach coach = coachRepository.findByUserId(coachUser.getId()).orElseGet(() -> {
                Coach created = Coach.create(coachUser);
                coachRepository.persist(created);
                return created;
            });
            coach.setOffersSingles(true);
            coach.setOffersDoubles(true);
            coach.setOffersGroup(false);
            clubCoachRepository.persist(ClubCoach.create(clubA, coach));
            clubCoachRepository.persist(ClubCoach.create(clubB, coach));
            clubAId = clubA.getId();
            clubBId = clubB.getId();
            clubCId = clubC.getId();
            coachId = coach.getId();
        });
    }

    private Club club(String prefix) {
        Club club = Club.create(prefix + " " + UUID.randomUUID());
        clubRepository.persist(club);
        return club;
    }

    private User user(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId).orElseGet(() -> {
            User user = new User();
            user.setKeycloakId(keycloakId);
            user.setName(keycloakId);
            user.setEmail(keycloakId + "@example.com");
            userRepository.persist(user);
            return user;
        });
    }

    private String pricesUrl(long clubId) {
        return "/api/admin/clubs/%d/coaches/%d/prices".formatted(clubId, coachId);
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void coach_changesTheirOwnOfferedTypes() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"singles\":false,\"doubles\":true,\"group\":true}")
                .when().put("/api/coach/me/offers")
                .then().statusCode(200)
                .body("singles", equalTo(false))
                .body("doubles", equalTo(true))
                .body("group", equalTo(true));

        Coach stored = QuarkusTransaction.requiringNew().call(() -> coachRepository.findById(coachId));
        assertFalse(stored.isOffersSingles());
        assertTrue(stored.isOffersDoubles());
        assertTrue(stored.isOffersGroup());
    }

    @Test
    @TestSecurity(user = COMMON)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COMMON)})
    void someoneWhoIsNotACoach_cannotChangeOffers_returns403() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"singles\":false,\"doubles\":false,\"group\":true}")
                .when().put("/api/coach/me/offers")
                .then().statusCode(403);

        Coach stored = QuarkusTransaction.requiringNew().call(() -> coachRepository.findById(coachId));
        assertTrue(stored.isOffersSingles());
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void admin_setsThePricesOfTheirClubOnly_andOmittedTypesStayWithoutPrice() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"singlesCents\":15000,\"doublesCents\":20000}")
                .when().put(pricesUrl(clubAId))
                .then().statusCode(200)
                .body("clubId", equalTo((int) clubAId))
                .body("coachId", equalTo((int) coachId))
                .body("singlesCents", equalTo(15000))
                .body("doublesCents", equalTo(20000))
                .body("groupCents", nullValue());

        ClubCoach inA = link(clubAId);
        assertEquals(15000L, inA.getPriceSinglesCents());
        assertEquals(20000L, inA.getPriceDoublesCents());
        assertNull(inA.getPriceGroupCents());
        ClubCoach inB = link(clubBId);
        assertNull(inB.getPriceSinglesCents());
        assertNull(inB.getPriceDoublesCents());
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void zeroOrNegativePrice_returns400AndChangesNothing() {
        for (String body : new String[]{
                "{\"singlesCents\":0}",
                "{\"doublesCents\":-100}",
                "{\"singlesCents\":15000,\"groupCents\":0}"
        }) {
            given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when().put(pricesUrl(clubAId))
                    .then().statusCode(400);
        }

        assertNull(link(clubAId).getPriceSinglesCents());
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void adminOfAnotherClub_cannotSetPrices_returns403() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"singlesCents\":15000}")
                .when().put(pricesUrl(clubBId))
                .then().statusCode(403);

        assertNull(link(clubBId).getPriceSinglesCents());
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void theCoachThemselves_cannotSetPrices_returns403() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"singlesCents\":1}")
                .when().put(pricesUrl(clubAId))
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = ADMIN_B)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_B)})
    void coachNotLinkedToTheAdminsClub_returns404() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"singlesCents\":15000}")
                .when().put(pricesUrl(clubCId))
                .then().statusCode(404);
    }

    private ClubCoach link(long clubId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            ClubCoach link = clubCoachRepository.findByClubAndCoach(clubId, coachId).orElseThrow();
            link.getClub().getId();
            return link;
        });
    }
}
