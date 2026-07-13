package br.com.stringtracker.service;

import br.com.stringtracker.client.KeycloakTokenClient;
import br.com.stringtracker.dto.LoginResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ProcessingException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class AuthService {

    @Inject
    @RestClient
    KeycloakTokenClient keycloakTokenClient;

    @ConfigProperty(name = "auth.keycloak.client-id")
    String clientId;

    public LoginResponse login(String username, String password) {
        final KeycloakTokenClient.TokenResponse token;
        try {
            token = keycloakTokenClient.passwordGrant("password", clientId, username, password);
        } catch (WebApplicationException e) {
            int status = e.getResponse() != null ? e.getResponse().getStatus() : 0;
            if (status == 400 || status == 401) {
                throw new InvalidCredentialsException("Usuário ou senha inválidos");
            }
            throw new IdentityProviderUnavailableException(
                    "Provedor de identidade indisponível", e);
        } catch (ProcessingException e) {
            throw new IdentityProviderUnavailableException(
                    "Não foi possível contactar o provedor de identidade", e);
        }

        if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
            throw new IdentityProviderUnavailableException(
                    "Provedor de identidade não retornou access_token");
        }
        return LoginResponse.bearer(token.accessToken(), token.expiresIn() > 0 ? token.expiresIn() : 300);
    }
}
