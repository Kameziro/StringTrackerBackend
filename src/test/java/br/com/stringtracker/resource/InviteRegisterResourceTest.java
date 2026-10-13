package br.com.stringtracker.resource;

import br.com.stringtracker.client.KeycloakAdminClient;
import br.com.stringtracker.client.KeycloakTokenClient;
import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.Invite;
import br.com.stringtracker.model.InviteKind;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.ClubAdminRepository;
import br.com.stringtracker.repository.ClubCoachRepository;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.repository.CoachRepository;
import br.com.stringtracker.repository.InviteRepository;
import br.com.stringtracker.repository.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cadastro na página do convite: o Keycloak é simulado (o token do convite é a credencial e a conta
 * nasce com o e-mail do convite), o resto, inclusive o banco, é real.
 */
@QuarkusTest
class InviteRegisterResourceTest {

    private static final long CITY_ID = 1L;

    @Inject
    ClubRepository clubRepository;

    @Inject
    InviteRepository inviteRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    ClubAdminRepository clubAdminRepository;

    @Inject
    CoachRepository coachRepository;

    @Inject
    ClubCoachRepository clubCoachRepository;

    private KeycloakAdminClient admin;
    private KeycloakTokenClient tokens;
    private String keycloakId;

    @BeforeEach
    void mockKeycloak() {
        keycloakId = "kc-invite-register-" + UUID.randomUUID();
        admin = mock(KeycloakAdminClient.class);
        tokens = mock(KeycloakTokenClient.class);
        when(admin.adminPasswordGrant(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new KeycloakAdminClient.AdminTokenResponse("admin-token"));
        when(admin.createUser(anyString(), anyString(), any()))
                .thenAnswer(call -> Response.created(java.net.URI.create("http://kc/users/" + keycloakId)).build());
        when(admin.resetPassword(anyString(), anyString(), anyString(), any()))
                .thenAnswer(call -> Response.noContent().build());
        when(admin.updateUser(anyString(), anyString(), anyString(), any()))
                .thenAnswer(call -> Response.noContent().build());
        when(tokens.passwordGrant(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(call -> new KeycloakTokenClient.TokenResponse(
                        fakeJwt(keycloakId), 300, "refresh-token", 1800, "Bearer"));
        QuarkusMock.installMockForType(admin, KeycloakAdminClient.class, RestClient.LITERAL);
        QuarkusMock.installMockForType(tokens, KeycloakTokenClient.class, RestClient.LITERAL);
    }

    @Test
    void register_clubAdminInvite_createsTheAccountWithTheInviteEmail_andMakesThemAdmin() {
        Club club = newClub();
        String email = unique("novo.admin") + "@example.com";
        String token = seedInvite(email, InviteKind.CLUB_ADMIN, club, Instant.now().plus(Duration.ofDays(1)));

        register(token, """
                {"name":"Marta Admin","password":"segredo1","cityId":1}
                """)
                .statusCode(201)
                .body("accessToken", notNullValue())
                .body("refreshToken", equalTo("refresh-token"))
                .body("tokenType", equalTo("Bearer"));

        ArgumentCaptor<KeycloakAdminClient.CreateUserPayload> payload =
                ArgumentCaptor.forClass(KeycloakAdminClient.CreateUserPayload.class);
        verify(admin).createUser(anyString(), anyString(), payload.capture());
        assertEquals(email, payload.getValue().email());

        QuarkusTransaction.requiringNew().run(() -> {
            User user = userRepository.findByKeycloakId(keycloakId).orElseThrow();
            assertEquals(email, user.getEmail());
            assertEquals("Marta Admin", user.getName());
            assertNull(user.getCategory());
            assertEquals(CITY_ID, user.getCity().getId());
            assertTrue(clubAdminRepository.isAdmin(club.getId(), user.getId()));
            Invite invite = inviteRepository.findByTokenHash(sha256Hex(token)).orElseThrow();
            assertNotNull(invite.getAcceptedAt());
            assertEquals(user.getId(), invite.getAcceptedBy().getId());
        });
    }

    @Test
    void register_coachInvite_linksTheNewCoachToTheClub() {
        Club club = newClub();
        String token = seedInvite(unique("novo.prof") + "@example.com", InviteKind.COACH, club,
                Instant.now().plus(Duration.ofDays(1)));

        register(token, """
                {"name":"Ana Professora","password":"segredo1","cityId":1,"category":4}
                """).statusCode(201);

        QuarkusTransaction.requiringNew().run(() -> {
            User user = userRepository.findByKeycloakId(keycloakId).orElseThrow();
            assertEquals(4, user.getCategory());
            long coachId = coachRepository.findByUserId(user.getId()).orElseThrow().getId();
            assertTrue(clubCoachRepository.findByClubAndCoach(club.getId(), coachId).orElseThrow().isActive());
        });
    }

    @Test
    void register_ignoresAnEmailSentByTheClient() {
        String email = unique("dono") + "@example.com";
        String token = seedInvite(email, InviteKind.CLUB_ADMIN, newClub(), Instant.now().plus(Duration.ofDays(1)));

        register(token, """
                {"name":"Marta Admin","password":"segredo1","cityId":1,"email":"intruso@example.com"}
                """).statusCode(201);

        ArgumentCaptor<KeycloakAdminClient.CreateUserPayload> payload =
                ArgumentCaptor.forClass(KeycloakAdminClient.CreateUserPayload.class);
        verify(admin).createUser(anyString(), anyString(), payload.capture());
        assertEquals(email, payload.getValue().email());
        assertTrue(userRepository.findByEmail("intruso@example.com").isEmpty());
    }

    @Test
    void register_unknownToken_returns404_andNeverTouchesKeycloak() {
        register("never-issued", """
                {"name":"Marta Admin","password":"segredo1","cityId":1}
                """)
                .statusCode(404)
                .body(equalTo("Convite não encontrado"));

        verifyNoInteractions(admin, tokens);
    }

    @Test
    void register_expiredToken_returns410_andNeverTouchesKeycloak() {
        String token = seedInvite(unique("exp") + "@example.com", InviteKind.COACH, newClub(),
                Instant.now().minus(Duration.ofMinutes(1)));

        register(token, """
                {"name":"Marta Admin","password":"segredo1","cityId":1}
                """)
                .statusCode(410)
                .body(equalTo("Convite expirado. Peça um novo ao clube"));

        verifyNoInteractions(admin, tokens);
    }

    @Test
    void register_secondUseOfTheSameToken_returns410() {
        String token = seedInvite(unique("uma.vez") + "@example.com", InviteKind.CLUB_ADMIN, newClub(),
                Instant.now().plus(Duration.ofDays(1)));
        String body = """
                {"name":"Marta Admin","password":"segredo1","cityId":1}
                """;
        register(token, body).statusCode(201);

        register(token, body)
                .statusCode(410)
                .body(equalTo("Este convite já foi aceito"));

        verify(admin).createUser(anyString(), anyString(), any());
    }

    @Test
    void register_emailAlreadyHasAnAccount_returns409_andKeepsTheInvitePending() {
        String email = unique("ja.tem") + "@example.com";
        QuarkusTransaction.requiringNew().run(() -> {
            User existing = new User();
            existing.setKeycloakId("kc-existing-" + UUID.randomUUID());
            existing.setEmail(email);
            existing.setName("Já Tem Conta");
            userRepository.persist(existing);
        });
        when(admin.createUser(anyString(), anyString(), any()))
                .thenAnswer(call -> Response.status(409).build());
        String token = seedInvite(email, InviteKind.CLUB_ADMIN, newClub(), Instant.now().plus(Duration.ofDays(1)));

        register(token, """
                {"name":"Marta Admin","password":"segredo1","cityId":1}
                """)
                .statusCode(409)
                .body("error", equalTo("Já existe uma conta com este e-mail"));

        assertPending(token);
    }

    @Test
    void register_interruptedEarlierAttempt_resumesWhenThePasswordMatches() {
        String email = unique("retoma") + "@example.com";
        Club club = newClub();
        String token = seedInvite(email, InviteKind.CLUB_ADMIN, club, Instant.now().plus(Duration.ofDays(1)));
        when(admin.createUser(anyString(), anyString(), any()))
                .thenAnswer(call -> Response.status(409).build());
        when(admin.findUsersByEmail(anyString(), anyString(), eq(email), anyBoolean()))
                .thenReturn(List.of(new KeycloakAdminClient.KeycloakUser(keycloakId, email, email)));

        register(token, """
                {"name":"Marta Admin","password":"segredo1","cityId":1}
                """).statusCode(201);

        QuarkusTransaction.requiringNew().run(() -> {
            User user = userRepository.findByKeycloakId(keycloakId).orElseThrow();
            assertTrue(clubAdminRepository.isAdmin(club.getId(), user.getId()));
        });
    }

    @Test
    void register_invalidBody_returns400_andNeverTouchesKeycloak() {
        String token = seedInvite(unique("invalido") + "@example.com", InviteKind.CLUB_ADMIN, newClub(),
                Instant.now().plus(Duration.ofDays(1)));

        for (String body : List.of(
                "{\"name\":\"A\",\"password\":\"segredo1\",\"cityId\":1}",
                "{\"name\":\"Marta Admin\",\"password\":\"12345\",\"cityId\":1}",
                "{\"name\":\"Marta Admin\",\"password\":\"segredo1\"}",
                "{\"name\":\"Marta Admin\",\"password\":\"segredo1\",\"cityId\":1,\"category\":9}")) {
            register(token, body).statusCode(400);
        }

        verifyNoInteractions(admin, tokens);
        assertPending(token);
    }

    @Test
    void register_unknownCity_returns404_andCreatesNoAccount() {
        String token = seedInvite(unique("cidade") + "@example.com", InviteKind.CLUB_ADMIN, newClub(),
                Instant.now().plus(Duration.ofDays(1)));

        register(token, """
                {"name":"Marta Admin","password":"segredo1","cityId":999999999}
                """)
                .statusCode(404)
                .body(equalTo("Cidade não encontrada"));

        verify(admin, never()).createUser(anyString(), anyString(), any());
        assertPending(token);
    }

    @Test
    void register_identityProviderDown_returns502_andKeepsTheInvitePending() {
        when(admin.adminPasswordGrant(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new ProcessingException("keycloak fora do ar"));
        String token = seedInvite(unique("fora") + "@example.com", InviteKind.CLUB_ADMIN, newClub(),
                Instant.now().plus(Duration.ofDays(1)));

        register(token, """
                {"name":"Marta Admin","password":"segredo1","cityId":1}
                """).statusCode(502);

        assertPending(token);
    }

    private ValidatableResponse register(String token, String body) {
        return given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/invites/" + token + "/register")
                .then();
    }

    private void assertPending(String token) {
        QuarkusTransaction.requiringNew().run(() -> {
            Optional<Invite> invite = inviteRepository.findByTokenHash(sha256Hex(token));
            assertFalse(invite.isEmpty());
            assertNull(invite.get().getAcceptedAt());
        });
    }

    private Club newClub() {
        return QuarkusTransaction.requiringNew().call(() -> {
            Club club = Club.create("Convite Cadastro " + UUID.randomUUID());
            clubRepository.persist(club);
            return club;
        });
    }

    /** Grava um convite direto no banco e devolve o token cru (o banco guarda só o hash). */
    private String seedInvite(String email, InviteKind kind, Club club, Instant expiresAt) {
        String token = "tok-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> inviteRepository.persist(
                Invite.create(sha256Hex(token), email, kind, club, null, expiresAt)));
        return token;
    }

    private static String unique(String prefix) {
        return prefix + "." + UUID.randomUUID();
    }

    /** JWT sem assinatura: o cadastro só lê o `sub` do payload. */
    private static String fakeJwt(String subject) {
        String payload = "{\"sub\":\"" + subject + "\"}";
        return "e30." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".assinatura";
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
