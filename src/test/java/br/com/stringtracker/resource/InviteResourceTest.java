package br.com.stringtracker.resource;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.Invite;
import br.com.stringtracker.model.InviteKind;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.repository.InviteRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class InviteResourceTest {

    private static final String GUEST = "kc-invite-res-guest";

    @Inject
    ClubRepository clubRepository;

    @Inject
    InviteRepository inviteRepository;

    @Test
    void get_validToken_returnsTheClubWithoutLogin() {
        Club club = newClub();
        String token = seedInvite(InviteKind.CLUB_ADMIN, club, Instant.now().plus(Duration.ofDays(1)));

        given()
                .when().get("/api/invites/" + token)
                .then().statusCode(200)
                .body("kind", equalTo("CLUB_ADMIN"))
                .body("clubId", equalTo(club.getId().intValue()))
                .body("clubName", equalTo(club.getName()))
                .body("expiresAt", notNullValue());
    }

    @Test
    void get_unknownToken_returns404() {
        given()
                .when().get("/api/invites/never-issued")
                .then().statusCode(404);
    }

    @Test
    void get_expiredToken_returns410() {
        String token = seedInvite(InviteKind.COACH, newClub(), Instant.now().minus(Duration.ofMinutes(1)));

        given()
                .when().get("/api/invites/" + token)
                .then().statusCode(410)
                .body(equalTo("Convite expirado. Peça um novo ao clube"));
    }

    @Test
    @TestSecurity(user = GUEST)
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = GUEST),
            @Claim(key = "email", value = "guest.invite.res@example.com"),
            @Claim(key = "name", value = "Guest")
    })
    void accept_validToken_linksTheUserAndReturnsTheClub() {
        Club club = newClub();
        String token = seedInvite(InviteKind.CLUB_ADMIN, club, Instant.now().plus(Duration.ofDays(1)));

        given()
                .when().post("/api/invites/" + token + "/accept")
                .then().statusCode(200)
                .body("kind", equalTo("CLUB_ADMIN"))
                .body("clubId", equalTo(club.getId().intValue()))
                .body("clubName", equalTo(club.getName()));
    }

    @Test
    @TestSecurity(user = GUEST)
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = GUEST),
            @Claim(key = "email", value = "guest.invite.res@example.com"),
            @Claim(key = "name", value = "Guest")
    })
    void accept_unknownToken_returns404() {
        given()
                .when().post("/api/invites/never-issued/accept")
                .then().statusCode(404);
    }

    @Test
    @TestSecurity(user = GUEST)
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = GUEST),
            @Claim(key = "email", value = "guest.invite.res@example.com"),
            @Claim(key = "name", value = "Guest")
    })
    void accept_expiredToken_returns410() {
        String token = seedInvite(InviteKind.COACH, newClub(), Instant.now().minus(Duration.ofMinutes(1)));

        given()
                .when().post("/api/invites/" + token + "/accept")
                .then().statusCode(410)
                .body(equalTo("Convite expirado. Peça um novo ao clube"));
    }

    @Test
    @TestSecurity(user = GUEST)
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = GUEST),
            @Claim(key = "email", value = "guest.invite.res@example.com"),
            @Claim(key = "name", value = "Guest")
    })
    void accept_alreadyAcceptedToken_returns410() {
        String token = seedInvite(InviteKind.CLUB_ADMIN, newClub(), Instant.now().plus(Duration.ofDays(1)));
        given().when().post("/api/invites/" + token + "/accept").then().statusCode(200);

        given()
                .when().post("/api/invites/" + token + "/accept")
                .then().statusCode(410)
                .body(equalTo("Este convite já foi aceito"));
    }

    @Test
    void accept_withoutLogin_returns401() {
        String token = seedInvite(InviteKind.CLUB_ADMIN, newClub(), Instant.now().plus(Duration.ofDays(1)));

        given()
                .when().post("/api/invites/" + token + "/accept")
                .then().statusCode(401);
    }

    private Club newClub() {
        return QuarkusTransaction.requiringNew().call(() -> {
            Club club = Club.create("Convite Rota " + UUID.randomUUID());
            clubRepository.persist(club);
            return club;
        });
    }

    /** Grava um convite direto no banco e devolve o token cru (o banco guarda só o hash). */
    private String seedInvite(InviteKind kind, Club club, Instant expiresAt) {
        String token = "tok-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> inviteRepository.persist(
                Invite.create(sha256Hex(token), "invited@example.com", kind, club, null, expiresAt)));
        return token;
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
