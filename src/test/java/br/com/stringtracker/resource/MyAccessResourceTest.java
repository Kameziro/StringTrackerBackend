package br.com.stringtracker.resource;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.ClubAdminRepository;
import br.com.stringtracker.repository.ClubCoachRepository;
import br.com.stringtracker.support.ScheduleFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

@QuarkusTest
class MyAccessResourceTest {

    private static final String ADMIN = "kc-myaccess-admin";
    private static final String EVERYTHING = "kc-myaccess-everything";
    private static final String COACH = "kc-myaccess-coach";
    private static final String FORMER_COACH = "kc-myaccess-former-coach";
    private static final String COMMON = "kc-myaccess-common";

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    ClubAdminRepository clubAdminRepository;

    @Inject
    ClubCoachRepository clubCoachRepository;

    private long clubAId;
    private long clubBId;

    /**
     * Os vínculos dos testes anteriores são desativados porque os usuários se repetem entre eles. ADMIN administra os
     * clubes B e A (criados nessa ordem; o A tem logo), mais um clube desativado e um vínculo de admin desativado.
     * EVERYTHING é admin da plataforma, do clube A e professor no B. COACH atende no clube B. FORMER_COACH só tem um
     * vínculo desativado e um com clube desativado.
     */
    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            User admin = fixtures.user(ADMIN);
            User everything = fixtures.user(EVERYTHING);
            User coachUser = fixtures.user(COACH);
            User formerCoachUser = fixtures.user(FORMER_COACH);
            fixtures.user(COMMON);
            everything.setPlatformAdmin(true);
            for (User user : new User[]{admin, everything}) {
                clubAdminRepository.update("active = false where user.id = ?1", user.getId());
            }
            for (User user : new User[]{everything, coachUser, formerCoachUser}) {
                clubCoachRepository.update("active = false where coach.user.id = ?1", user.getId());
            }

            Club clubB = fixtures.club("Acesso Clube B");
            Club clubA = fixtures.club("Acesso Clube A");
            clubA.setLogoUrl("/api/media/clubs/1/logo.png");
            Club clubGone = fixtures.club("Acesso Clube Desativado");
            Club clubUnlinked = fixtures.club("Acesso Clube Desvinculado");
            fixtures.admin(clubB, admin);
            fixtures.admin(clubA, admin);
            fixtures.admin(clubGone, admin);
            fixtures.admin(clubUnlinked, admin);
            fixtures.admin(clubA, everything);
            clubAdminRepository.update("active = false where club.id = ?1 and user.id = ?2",
                    clubUnlinked.getId(), admin.getId());

            Coach everythingCoach = fixtures.coach(everything);
            Coach coach = fixtures.coach(coachUser);
            Coach formerCoach = fixtures.coach(formerCoachUser);
            fixtures.link(clubB, everythingCoach, 9000L, null);
            fixtures.link(clubB, coach, 9000L, null);
            fixtures.link(clubA, formerCoach, 9000L, null).markExcluded();
            fixtures.link(clubGone, formerCoach, 9000L, null);
            clubGone.markExcluded();
            clubAId = clubA.getId();
            clubBId = clubB.getId();
        });
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void clubAdmin_getsTheirActiveClubsByName_withoutOtherRoles() {
        given().when().get("/api/me/access")
                .then().statusCode(200)
                .body("platformAdmin", equalTo(false))
                .body("coach", equalTo(false))
                .body("adminClubs.id", contains((int) clubAId, (int) clubBId))
                .body("adminClubs[0].name", startsWith("Acesso Clube A"))
                .body("adminClubs[0].logoUrl", equalTo("/api/media/clubs/1/logo.png"))
                .body("adminClubs[1].logoUrl", nullValue());
    }

    @Test
    @TestSecurity(user = EVERYTHING)
    @JwtSecurity(claims = {@Claim(key = "sub", value = EVERYTHING)})
    void platformAdminWhoIsAlsoClubAdminAndCoach_getsAllThreeRoles() {
        given().when().get("/api/me/access")
                .then().statusCode(200)
                .body("platformAdmin", equalTo(true))
                .body("coach", equalTo(true))
                .body("adminClubs.id", contains((int) clubAId));
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void coachWithAnActiveClub_isACoachButNotAnAdmin() {
        given().when().get("/api/me/access")
                .then().statusCode(200)
                .body("platformAdmin", equalTo(false))
                .body("coach", equalTo(true))
                .body("adminClubs", empty());
    }

    @Test
    @TestSecurity(user = FORMER_COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = FORMER_COACH)})
    void coachWhoseLinksAreUnlinkedOrOfDeactivatedClubs_isNotACoach() {
        given().when().get("/api/me/access")
                .then().statusCode(200)
                .body("coach", equalTo(false));
    }

    @Test
    @TestSecurity(user = COMMON)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COMMON)})
    void ordinaryUser_hasNoRole() {
        given().when().get("/api/me/access")
                .then().statusCode(200)
                .body("platformAdmin", equalTo(false))
                .body("coach", equalTo(false))
                .body("adminClubs", empty());
    }

    @Test
    void withoutAToken_returns401() {
        given().when().get("/api/me/access").then().statusCode(401);
    }
}
