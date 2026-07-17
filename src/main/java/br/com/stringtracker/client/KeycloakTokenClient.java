package br.com.stringtracker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestForm;

@RegisterRestClient(configKey = "keycloak-token")
public interface KeycloakTokenClient {

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    TokenResponse passwordGrant(
            @RestForm("grant_type") String grantType,
            @RestForm("client_id") String clientId,
            @RestForm("username") String username,
            @RestForm("password") String password
    );

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    TokenResponse refreshGrant(
            @RestForm("grant_type") String grantType,
            @RestForm("client_id") String clientId,
            @RestForm("refresh_token") String refreshToken
    );

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("refresh_expires_in") long refreshExpiresIn,
            @JsonProperty("token_type") String tokenType
    ) {
    }
}
