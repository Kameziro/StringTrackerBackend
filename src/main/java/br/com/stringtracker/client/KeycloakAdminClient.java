package br.com.stringtracker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestHeader;

import java.util.List;

@RegisterRestClient(configKey = "keycloak-admin")
public interface KeycloakAdminClient {

    @POST
    @Path("/realms/master/protocol/openid-connect/token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    AdminTokenResponse adminPasswordGrant(
            @RestForm("grant_type") String grantType,
            @RestForm("client_id") String clientId,
            @RestForm("username") String username,
            @RestForm("password") String password
    );

    @POST
    @Path("/admin/realms/{realm}/users")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response createUser(
            @PathParam("realm") String realm,
            @RestHeader("Authorization") String authorization,
            CreateUserPayload payload
    );

    @GET
    @Path("/admin/realms/{realm}/users")
    @Produces(MediaType.APPLICATION_JSON)
    List<KeycloakUser> findUsersByEmail(
            @PathParam("realm") String realm,
            @RestHeader("Authorization") String authorization,
            @QueryParam("email") String email,
            @QueryParam("exact") boolean exact
    );

    @PUT
    @Path("/admin/realms/{realm}/users/{userId}")
    @Consumes(MediaType.APPLICATION_JSON)
    Response updateUser(
            @PathParam("realm") String realm,
            @PathParam("userId") String userId,
            @RestHeader("Authorization") String authorization,
            UpdateUserPayload payload
    );

    @PUT
    @Path("/admin/realms/{realm}/users/{userId}/reset-password")
    @Consumes(MediaType.APPLICATION_JSON)
    Response resetPassword(
            @PathParam("realm") String realm,
            @PathParam("userId") String userId,
            @RestHeader("Authorization") String authorization,
            Credential credential
    );

    @POST
    @Path("/realms/{realm}/protocol/openid-connect/logout")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    Response logout(
            @PathParam("realm") String realm,
            @RestForm("client_id") String clientId,
            @RestForm("refresh_token") String refreshToken
    );

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdminTokenResponse(
            @JsonProperty("access_token") String accessToken
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KeycloakUser(String id, String email, String username) {
    }

    /** Cria usuário sem senha — a senha é definida em {@link #resetPassword}. */
    record CreateUserPayload(
            String username,
            String email,
            boolean enabled,
            boolean emailVerified,
            String firstName,
            String lastName
    ) {
        public static CreateUserPayload of(String email, String name) {
            NameParts parts = NameParts.from(email, name);
            String normalized = email.trim().toLowerCase();
            return new CreateUserPayload(
                    normalized,
                    normalized,
                    true,
                    true,
                    parts.firstName(),
                    parts.lastName()
            );
        }
    }

    /** Completa perfil exigido pelo User Profile do Keycloak (firstName/lastName). */
    record UpdateUserPayload(
            String username,
            String email,
            boolean enabled,
            boolean emailVerified,
            String firstName,
            String lastName,
            java.util.List<String> requiredActions
    ) {
        public static UpdateUserPayload profile(String email, String name) {
            NameParts parts = NameParts.from(email, name);
            String normalized = email.trim().toLowerCase();
            return new UpdateUserPayload(
                    normalized,
                    normalized,
                    true,
                    true,
                    parts.firstName(),
                    parts.lastName(),
                    java.util.List.of()
            );
        }
    }

    record NameParts(String firstName, String lastName) {
        static NameParts from(String email, String name) {
            String fallback = email == null || email.isBlank() ? "Jogador" : email.split("@")[0];
            String trimmed = name == null || name.isBlank() ? fallback : name.trim();
            String[] tokens = trimmed.split("\\s+", 2);
            String first = tokens[0].isBlank() ? fallback : tokens[0];
            // Keycloak VERIFY_PROFILE exige lastName — usa placeholder se só houver um nome.
            String last = tokens.length > 1 && !tokens[1].isBlank() ? tokens[1] : first;
            return new NameParts(first, last);
        }
    }

    record Credential(String type, String value, boolean temporary) {
        public static Credential password(String value) {
            return new Credential("password", value, false);
        }
    }
}
