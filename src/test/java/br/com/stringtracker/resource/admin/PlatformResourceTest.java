package br.com.stringtracker.resource.admin;

import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.CityRepository;
import br.com.stringtracker.repository.UserRepository;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PlatformResourceTest {

    private static final String PLATFORM = "kc-platform-admin";
    private static final String COMMON = "kc-platform-common";
    private static final long CITY_ID = 5565L;

    @Inject
    UserRepository userRepository;

    @Inject
    CityRepository cityRepository;

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    void seed() {
        mailbox.clear();
        QuarkusTransaction.requiringNew().run(() -> {
            userRepository.findByKeycloakId(PLATFORM).orElseGet(() -> {
                User user = new User();
                user.setKeycloakId(PLATFORM);
                user.setName("Plataforma");
                user.setEmail("platform.admin@example.com");
                user.setPlatformAdmin(true);
                userRepository.persist(user);
                return user;
            });
        });
    }

    @Test
    @TestSecurity(user = PLATFORM)
    @JwtSecurity(claims = {@Claim(key = "sub", value = PLATFORM)})
    void createClub_withNameAndCity_appearsInTheList() {
        String name = "Clube Plataforma " + UUID.randomUUID();
        String cityName = QuarkusTransaction.requiringNew().call(() -> cityRepository.findById(CITY_ID).getName());

        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"%s\",\"cityId\":%d}".formatted(name, CITY_ID))
                .when().post("/api/platform/clubs")
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo(name))
                .body("cityId", equalTo((int) CITY_ID))
                .body("cityName", equalTo(cityName))
                .body("paymentStatus", equalTo("NOT_CONNECTED"));

        given()
                .when().get("/api/platform/clubs")
                .then().statusCode(200)
                .body("find { it.name == '%s' }.cityId".formatted(name), equalTo((int) CITY_ID))
                .body("find { it.name == '%s' }.cityName".formatted(name), equalTo(cityName));
    }

    @Test
    @TestSecurity(user = PLATFORM)
    @JwtSecurity(claims = {@Claim(key = "sub", value = PLATFORM)})
    void createClub_withAnExistingName_returns400() {
        String name = "Clube Repetido " + UUID.randomUUID();
        String body = "{\"name\":\"%s\",\"cityId\":%d}".formatted(name, CITY_ID);
        given().contentType(ContentType.JSON).body(body).when().post("/api/platform/clubs").then().statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(body.replace(name, name.toUpperCase()))
                .when().post("/api/platform/clubs")
                .then().statusCode(400)
                .body(equalTo("Já existe um clube com esse nome"));
    }

    @Test
    @TestSecurity(user = PLATFORM)
    @JwtSecurity(claims = {@Claim(key = "sub", value = PLATFORM)})
    void createClub_withUnknownCityOrBlankName_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Clube Sem Cidade %s\",\"cityId\":999999999}".formatted(UUID.randomUUID()))
                .when().post("/api/platform/clubs")
                .then().statusCode(400);

        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"  \",\"cityId\":%d}".formatted(CITY_ID))
                .when().post("/api/platform/clubs")
                .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = PLATFORM)
    @JwtSecurity(claims = {@Claim(key = "sub", value = PLATFORM)})
    void inviteAdmin_sendsTheEmailAndReturnsTheLink() {
        long clubId = createClub();
        String email = "primeiro.admin." + UUID.randomUUID() + "@example.com";

        String link = given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"%s\"}".formatted(email))
                .when().post("/api/platform/clubs/%d/admin-invites".formatted(clubId))
                .then().statusCode(201)
                .body("email", equalTo(email))
                .body("kind", equalTo("CLUB_ADMIN"))
                .body("expiresAt", notNullValue())
                .extract().path("link");

        List<Mail> sent = mailbox.getMailsSentTo(email);
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).getText().contains(link));
    }

    @Test
    @TestSecurity(user = PLATFORM)
    @JwtSecurity(claims = {@Claim(key = "sub", value = PLATFORM)})
    void inviteAdmin_forUnknownClubOrInvalidEmail_isRejected() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"alguem@example.com\"}")
                .when().post("/api/platform/clubs/999999999/admin-invites")
                .then().statusCode(404);

        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"not-an-email\"}")
                .when().post("/api/platform/clubs/%d/admin-invites".formatted(createClub()))
                .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = COMMON)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COMMON)})
    void commonUser_isDeniedInThePlatformArea() {
        given().when().get("/api/platform/clubs").then().statusCode(403);

        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Clube Proibido\",\"cityId\":%d}".formatted(CITY_ID))
                .when().post("/api/platform/clubs")
                .then().statusCode(403);

        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"alguem@example.com\"}")
                .when().post("/api/platform/clubs/1/admin-invites")
                .then().statusCode(403);
    }

    private long createClub() {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Clube Convite %s\",\"cityId\":%d}".formatted(UUID.randomUUID(), CITY_ID))
                .when().post("/api/platform/clubs")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }
}
