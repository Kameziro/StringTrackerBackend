package br.com.stringtracker.resource;

import br.com.stringtracker.dto.UpdateProfileRequest;
import br.com.stringtracker.service.ExpoPushService;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class OpenGameResourceTest {

    @BeforeEach
    void mockPush() {
        ExpoPushService push = Mockito.mock(ExpoPushService.class);
        Mockito.when(push.gameData(Mockito.anyLong())).thenReturn(Map.of("gameId", "1", "type", "open_game"));
        QuarkusMock.installMockForType(push, ExpoPushService.class);
    }

    @Test
    @TestSecurity(user = "kc-match-org")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "kc-match-org"),
            @Claim(key = "email", value = "org@example.com"),
            @Claim(key = "name", value = "Organizador")
    })
    void createGame_categoryWide_and_groupScoped() {
        given()
                .contentType(ContentType.JSON)
                .body(new UpdateProfileRequest("Organizador", 5, false))
                .when().put("/api/me/profile")
                .then().statusCode(200);

        Instant start = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"place":"Inner Pad","startsAt":"%s","endsAt":"%s","category":5,"capacity":4}
                        """.formatted(start, end))
                .when().post("/api/games")
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("clubName", equalTo("Inner Pad"))
                .body("groupId", nullValue())
                .body("status", equalTo("OPEN"));

        Long groupId = given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Amigos Maranhão\"}")
                .when().post("/api/groups")
                .then().statusCode(201)
                .body("joined", equalTo(true))
                .extract().jsonPath().getLong("id");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"place":"Maranhão","startsAt":"%s","endsAt":"%s","category":5,"capacity":4,"groupId":%d}
                        """.formatted(start.plus(1, ChronoUnit.HOURS), end.plus(1, ChronoUnit.HOURS), groupId))
                .when().post("/api/games")
                .then().statusCode(201)
                .body("clubName", equalTo("Maranhão"))
                .body("groupId", equalTo(groupId.intValue()))
                .body("groupName", equalTo("Amigos Maranhão"));
    }
}
