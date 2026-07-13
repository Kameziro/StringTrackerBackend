package br.com.stringtracker.resource;

import br.com.stringtracker.model.Racket;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.RacketRepository;
import br.com.stringtracker.repository.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class PlaySessionResourceTest {

    private static final String RACKET_BODY = """
            {
              "brand": "Babolat",
              "model": "Pure Drive",
              "stringModel": "Luxilon Alu Power",
              "tensionLbs": 52.0,
              "dateStrung": "2026-06-01"
            }
            """;

    @Inject
    UserRepository userRepository;

    @Inject
    RacketRepository racketRepository;

    @Test
    @TestSecurity(user = "kc-session-1")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "kc-session-1"),
            @Claim(key = "email", value = "session@example.com"),
            @Claim(key = "name", value = "Session User")
    })
    void createSession_addsHoursToRacket_usingMinutesOverSixty() {
        Integer racketId = given()
                .contentType(ContentType.JSON)
                .body(RACKET_BODY)
                .when().post("/api/rackets")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "racketId": %d,
                          "durationMinutes": 90,
                          "datePlayed": "2026-07-12"
                        }
                        """.formatted(racketId))
                .when().post("/api/sessions")
                .then()
                .statusCode(201)
                .body("racketId", equalTo(racketId))
                .body("durationMinutes", equalTo(90))
                .body("datePlayed", equalTo("2026-07-12"))
                .body("racketTotalHoursPlayed", equalTo(1.5f));

        given()
                .when().get("/api/rackets")
                .then()
                .statusCode(200)
                .body("[0].totalHoursPlayed", equalTo(1.5f));
    }

    @Test
    @TestSecurity(user = "kc-session-404")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "kc-session-404"),
            @Claim(key = "email", value = "session404@example.com"),
            @Claim(key = "name", value = "Session 404")
    })
    void createSession_unknownRacket_returns404() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "racketId": 999999,
                          "durationMinutes": 60,
                          "datePlayed": "2026-07-12"
                        }
                        """)
                .when().post("/api/sessions")
                .then()
                .statusCode(404)
                .body(equalTo("Raquete não encontrada."));
    }

    @Test
    @TestSecurity(user = "kc-session-owner-b")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "kc-session-owner-b"),
            @Claim(key = "email", value = "owner-b@example.com"),
            @Claim(key = "name", value = "Owner B")
    })
    void createSession_racketOfAnotherUser_returns404() {
        Long foreignRacketId = seedRacketForOtherUser();

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "racketId": %d,
                          "durationMinutes": 60,
                          "datePlayed": "2026-07-12"
                        }
                        """.formatted(foreignRacketId))
                .when().post("/api/sessions")
                .then()
                .statusCode(404)
                .body(equalTo("Raquete não encontrada."));
    }

    @Test
    @TestSecurity(user = "kc-session-invalid-duration")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "kc-session-invalid-duration"),
            @Claim(key = "email", value = "invalid-duration@example.com"),
            @Claim(key = "name", value = "Invalid Duration")
    })
    void createSession_nonPositiveDuration_returns400() {
        Integer racketId = given()
                .contentType(ContentType.JSON)
                .body(RACKET_BODY)
                .when().post("/api/rackets")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "racketId": %d,
                          "durationMinutes": 0,
                          "datePlayed": "2026-07-12"
                        }
                        """.formatted(racketId))
                .when().post("/api/sessions")
                .then()
                .statusCode(400);
    }

    private Long seedRacketForOtherUser() {
        return QuarkusTransaction.requiringNew().call(() -> {
            User other = new User();
            other.setKeycloakId("kc-other-owner");
            other.setEmail("other@example.com");
            other.setName("Other Owner");
            other.setPremium(false);
            userRepository.persist(other);

            Racket racket = Racket.create(
                    other,
                    "Wilson",
                    "Blade",
                    "RPM Blast",
                    50.0,
                    LocalDate.of(2026, 1, 1)
            );
            racketRepository.persist(racket);
            return racket.getId();
        });
    }
}
