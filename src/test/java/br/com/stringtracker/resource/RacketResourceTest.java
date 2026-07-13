package br.com.stringtracker.resource;

import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class RacketResourceTest {

    private static final String CREATE_BODY = """
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

    @Test
    @TestSecurity(user = "kc-free-list")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "kc-free-list"),
            @Claim(key = "email", value = "free-list@example.com"),
            @Claim(key = "name", value = "Free List")
    })
    void listRackets_returnsEmptyWhenNone() {
        given()
                .when().get("/api/rackets")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    @TestSecurity(user = "kc-free-create")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "kc-free-create"),
            @Claim(key = "email", value = "free-create@example.com"),
            @Claim(key = "name", value = "Free Create")
    })
    void createRacket_firstForFreeUser_returns201() {
        given()
                .contentType(ContentType.JSON)
                .body(CREATE_BODY)
                .when().post("/api/rackets")
                .then()
                .statusCode(201)
                .body("brand", equalTo("Babolat"))
                .body("model", equalTo("Pure Drive"))
                .body("stringModel", equalTo("Luxilon Alu Power"))
                .body("tensionLbs", equalTo(52.0f))
                .body("dateStrung", equalTo("2026-06-01"))
                .body("totalHoursPlayed", equalTo(0.0f));
    }

    @Test
    @TestSecurity(user = "kc-free-limit")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "kc-free-limit"),
            @Claim(key = "email", value = "free-limit@example.com"),
            @Claim(key = "name", value = "Free Limit")
    })
    void createRacket_secondForFreeUser_returns403WithExactMessage() {
        given()
                .contentType(ContentType.JSON)
                .body(CREATE_BODY)
                .when().post("/api/rackets")
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(CREATE_BODY)
                .when().post("/api/rackets")
                .then()
                .statusCode(403)
                .contentType("text/plain")
                .body(equalTo(
                        "Limite de raquetes atingido para usuários gratuitos. Faça o upgrade para o Premium!"));
    }

    @Test
    @TestSecurity(user = "kc-premium-multi")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "kc-premium-multi"),
            @Claim(key = "email", value = "premium@example.com"),
            @Claim(key = "name", value = "Premium User")
    })
    void createRacket_premiumUser_canCreateMultiple() {
        seedPremiumUser("kc-premium-multi", "premium@example.com", "Premium User");

        given()
                .contentType(ContentType.JSON)
                .body(CREATE_BODY)
                .when().post("/api/rackets")
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(CREATE_BODY)
                .when().post("/api/rackets")
                .then()
                .statusCode(201);

        given()
                .when().get("/api/rackets")
                .then()
                .statusCode(200)
                .body("$", hasSize(2));
    }

    @Test
    void listRackets_withoutAuth_returns401() {
        given()
                .when().get("/api/rackets")
                .then()
                .statusCode(401);
    }

    private void seedPremiumUser(String keycloakId, String email, String name) {
        QuarkusTransaction.requiringNew().run(() -> {
            User user = userRepository.findByKeycloakId(keycloakId).orElseGet(() -> {
                User created = new User();
                created.setKeycloakId(keycloakId);
                created.setEmail(email);
                created.setName(name);
                created.setPremium(true);
                userRepository.persist(created);
                return created;
            });
            user.setPremium(true);
        });
    }
}
