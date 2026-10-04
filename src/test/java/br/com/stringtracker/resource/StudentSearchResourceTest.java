package br.com.stringtracker.resource;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.User;
import br.com.stringtracker.support.ScheduleFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class StudentSearchResourceTest {

    private static final String ADMIN = "kc-search-admin";
    private static final String COACH = "kc-search-coach";
    private static final String COMMON = "kc-search-common";

    @Inject
    ScheduleFixtures fixtures;

    private long clubId;
    private long targetId;
    private String targetName;
    private String targetEmail;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            Club club = fixtures.club("Busca Clube");
            fixtures.admin(club, fixtures.user(ADMIN));
            fixtures.coach(fixtures.user(COACH));
            fixtures.user(COMMON);
            clubId = club.getId();
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            targetName = "Zeferino Buscavel " + suffix;
            targetEmail = "zef-" + suffix + "@example.com";
            User target = fixtures.user("kc-search-target-" + suffix);
            target.setName(targetName);
            target.setEmail(targetEmail);
            targetId = target.getId();
        });
    }

    private String adminUrl() {
        return "/api/admin/clubs/" + clubId + "/students";
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void adminFindsByPartOfTheName_ignoringCase_withTheEmailMasked() {
        given().queryParam("q", targetName.toUpperCase().substring(0, 20)).when().get(adminUrl())
                .then().statusCode(200)
                .body("find { it.id == " + targetId + " }.name", equalTo(targetName))
                .body("find { it.id == " + targetId + " }.emailHint", equalTo("z***@example.com"));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void emailSearchIsExactAndIgnoresCase_soPartialEmailsFindNobody() {
        given().queryParam("q", targetEmail.toUpperCase()).when().get(adminUrl())
                .then().statusCode(200).body("id", contains((int) targetId));
        given().queryParam("q", "zef-@example").when().get(adminUrl())
                .then().statusCode(200).body("$", hasSize(0));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void likeWildcardsAreTakenLiterally_andShortOrMissingQueriesReturn400() {
        given().queryParam("q", "Zef%vel").when().get(adminUrl())
                .then().statusCode(200).body("$", hasSize(0));
        given().queryParam("q", "ab").when().get(adminUrl())
                .then().statusCode(400).body(equalTo("Digite ao menos 3 letras para buscar"));
        given().when().get(adminUrl()).then().statusCode(400);
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void coachCanSearchToo() {
        given().queryParam("q", targetName).when().get("/api/coach/me/students")
                .then().statusCode(200).body("id", contains((int) targetId));
    }

    @Test
    @TestSecurity(user = COMMON)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COMMON)})
    void aUserWhoIsNeitherAdminNorCoach_gets403() {
        given().queryParam("q", targetName).when().get(adminUrl()).then().statusCode(403);
        given().queryParam("q", targetName).when().get("/api/coach/me/students").then().statusCode(403);
    }
}
