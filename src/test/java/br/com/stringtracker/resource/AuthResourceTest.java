package br.com.stringtracker.resource;

import br.com.stringtracker.service.AuthService;
import br.com.stringtracker.dto.LoginResponse;
import br.com.stringtracker.service.InvalidCredentialsException;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
class AuthResourceTest {

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        QuarkusMock.installMockForType(authService, AuthService.class);
    }

    @Test
    void login_returnsTokensOnSuccess() {
        when(authService.login("free.player", "free123"))
                .thenReturn(new LoginResponse("jwt-token", "Bearer", 300));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"free.player","password":"free123"}
                        """)
                .when().post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", equalTo("jwt-token"))
                .body("tokenType", equalTo("Bearer"))
                .body("expiresIn", equalTo(300));
    }

    @Test
    void login_returns401OnInvalidCredentials() {
        when(authService.login(anyString(), anyString()))
                .thenThrow(new InvalidCredentialsException("Usuário ou senha inválidos"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"bad","password":"bad"}
                        """)
                .when().post("/api/auth/login")
                .then()
                .statusCode(401)
                .body(equalTo("Usuário ou senha inválidos"));
    }

    @Test
    void login_returns400WhenBodyInvalid() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"","password":""}
                        """)
                .when().post("/api/auth/login")
                .then()
                .statusCode(400);
    }
}
