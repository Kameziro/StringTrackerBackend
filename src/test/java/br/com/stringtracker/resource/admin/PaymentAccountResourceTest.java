package br.com.stringtracker.resource.admin;

import br.com.stringtracker.client.MercadoPagoOAuthClient;
import br.com.stringtracker.client.MercadoPagoOAuthClient.TokenResponse;
import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubAdmin;
import br.com.stringtracker.model.ClubPaymentStatus;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.ClubAdminRepository;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.repository.UserRepository;
import br.com.stringtracker.service.payment.OAuthStateSigner;
import br.com.stringtracker.service.payment.TokenCipher;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class PaymentAccountResourceTest {

    private static final String ADMIN_A = "kc-payacc-a";
    private static final String ADMIN_B = "kc-payacc-b";
    private static final String REDIRECT_URI = "http://localhost:3100/pagamentos/retorno";

    @InjectMock
    @RestClient
    MercadoPagoOAuthClient oauthClient;

    @Inject
    OAuthStateSigner signer;

    @Inject
    TokenCipher cipher;

    @Inject
    UserRepository userRepository;

    @Inject
    ClubRepository clubRepository;

    @Inject
    ClubAdminRepository clubAdminRepository;

    private long clubAId;
    private long clubBId;
    private long adminAId;
    private long adminBId;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            User adminA = user(ADMIN_A);
            User adminB = user(ADMIN_B);
            Club clubA = Club.create("Pagamento Clube A " + UUID.randomUUID());
            Club clubB = Club.create("Pagamento Clube B " + UUID.randomUUID());
            clubRepository.persist(clubA);
            clubRepository.persist(clubB);
            clubAdminRepository.persist(ClubAdmin.create(clubA, adminA));
            clubAdminRepository.persist(ClubAdmin.create(clubB, adminB));
            clubAId = clubA.getId();
            clubBId = clubB.getId();
            adminAId = adminA.getId();
            adminBId = adminB.getId();
        });
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

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void connectUrl_pointsToMercadoPagoWithASignedStateForThisAdminAndClub() {
        String url = given()
                .when().get("/api/admin/clubs/%d/payment-account/connect-url".formatted(clubAId))
                .then().statusCode(200)
                .extract().path("url");

        URI uri = URI.create(url);
        assertEquals("auth.mercadopago.com", uri.getHost());
        assertEquals("/authorization", uri.getPath());
        String query = uri.getRawQuery();
        assertTrue(query.contains("client_id=test-client-id"));
        assertTrue(query.contains("response_type=code"));
        assertTrue(query.contains("platform_id=mp"));
        assertTrue(query.contains("redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)));
        assertEquals(new OAuthStateSigner.OAuthState(clubAId, adminAId), signer.verify(stateOf(query), Instant.now()));
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void connectUrl_forAnotherClub_returns403() {
        given()
                .when().get("/api/admin/clubs/%d/payment-account/connect-url".formatted(clubBId))
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void callback_connectsTheClubStoresEncryptedTokensAndNeverReturnsThem() {
        when(oauthClient.exchangeCode("authorization_code", "test-client-id", "test-client-secret", "the-code", REDIRECT_URI))
                .thenReturn(new TokenResponse("AT-secret-value", "RT-secret-value", 21600, 123456789L));
        String state = signer.issue(clubAId, adminAId, Instant.now());

        String body = given()
                .queryParam("code", "the-code")
                .queryParam("state", state)
                .when().get("/api/admin/payment-account/callback")
                .then().statusCode(200)
                .body("paymentStatus", equalTo("CONNECTED"))
                .extract().asString();

        assertFalse(body.contains("AT-secret-value"));
        assertFalse(body.contains("RT-secret-value"));
        Club club = QuarkusTransaction.requiringNew().call(() -> clubRepository.findById(clubAId));
        assertEquals(ClubPaymentStatus.CONNECTED, club.getPaymentStatus());
        assertEquals("123456789", club.getMpUserId());
        assertNotEquals("AT-secret-value", club.getMpAccessTokenEnc());
        assertEquals("AT-secret-value", cipher.decrypt(club.getMpAccessTokenEnc()));
        assertNotEquals("RT-secret-value", club.getMpRefreshTokenEnc());
        assertEquals("RT-secret-value", cipher.decrypt(club.getMpRefreshTokenEnc()));
        Duration untilExpiry = Duration.between(Instant.now(), club.getMpTokenExpiresAt());
        assertTrue(untilExpiry.compareTo(Duration.ofHours(5).plusMinutes(59)) > 0
                && untilExpiry.compareTo(Duration.ofHours(6).plusMinutes(1)) < 0);

        given()
                .when().get("/api/admin/clubs/%d/profile".formatted(clubAId))
                .then().statusCode(200)
                .body("paymentStatus", equalTo("CONNECTED"))
                .body(not(containsString("secret-value")));
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void callback_withGarbageOrTamperedState_returns400WithoutCallingTheProvider() {
        String valid = signer.issue(clubAId, adminAId, Instant.now());

        for (String state : new String[]{"not-a-state", valid + "x"}) {
            given()
                    .queryParam("code", "the-code")
                    .queryParam("state", state)
                    .when().get("/api/admin/payment-account/callback")
                    .then().statusCode(400);
        }

        verify(oauthClient, never()).exchangeCode(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void callback_withAnExpiredState_returns400() {
        String expired = signer.issue(clubAId, adminAId, Instant.now().minus(Duration.ofMinutes(11)));

        given()
                .queryParam("code", "the-code")
                .queryParam("state", expired)
                .when().get("/api/admin/payment-account/callback")
                .then().statusCode(400);

        verify(oauthClient, never()).exchangeCode(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void callback_withAStateOfAnotherClubOrAnotherAdmin_returns400AndConnectsNothing() {
        String otherAdminsState = signer.issue(clubBId, adminBId, Instant.now());
        String stateForAClubIDoNotAdminister = signer.issue(clubBId, adminAId, Instant.now());

        for (String state : new String[]{otherAdminsState, stateForAClubIDoNotAdminister}) {
            given()
                    .queryParam("code", "the-code")
                    .queryParam("state", state)
                    .when().get("/api/admin/payment-account/callback")
                    .then().statusCode(400);
        }

        verify(oauthClient, never()).exchangeCode(anyString(), anyString(), anyString(), anyString(), anyString());
        Club clubB = QuarkusTransaction.requiringNew().call(() -> clubRepository.findById(clubBId));
        assertEquals(ClubPaymentStatus.NOT_CONNECTED, clubB.getPaymentStatus());
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void callback_whenTheProviderRefusesTheCode_returns502AndStoresNothing() {
        when(oauthClient.exchangeCode(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new WebApplicationException(400))
                .thenThrow(new ProcessingException("connection reset"));
        String state = signer.issue(clubAId, adminAId, Instant.now());

        for (int attempt = 0; attempt < 2; attempt++) {
            given()
                    .queryParam("code", "bad-code")
                    .queryParam("state", state)
                    .when().get("/api/admin/payment-account/callback")
                    .then().statusCode(502)
                    .body(equalTo("Não foi possível conectar ao Mercado Pago. Tente novamente"));
        }

        Club club = QuarkusTransaction.requiringNew().call(() -> clubRepository.findById(clubAId));
        assertEquals(ClubPaymentStatus.NOT_CONNECTED, club.getPaymentStatus());
        assertNull(club.getMpAccessTokenEnc());
        assertNull(club.getMpRefreshTokenEnc());
    }

    private static String stateOf(String rawQuery) {
        for (String pair : rawQuery.split("&")) {
            if (pair.startsWith("state=")) {
                return URLDecoder.decode(pair.substring("state=".length()), StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("state ausente em " + rawQuery);
    }
}
