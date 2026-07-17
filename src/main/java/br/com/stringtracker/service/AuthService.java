package br.com.stringtracker.service;

import br.com.stringtracker.client.KeycloakAdminClient;
import br.com.stringtracker.client.KeycloakTokenClient;
import br.com.stringtracker.dto.LoginResponse;
import br.com.stringtracker.model.City;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.CityRepository;
import br.com.stringtracker.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class);

    @Inject
    @RestClient
    KeycloakTokenClient keycloakTokenClient;

    @Inject
    @RestClient
    KeycloakAdminClient keycloakAdminClient;

    @Inject
    UserRepository userRepository;

    @Inject
    CityRepository cityRepository;

    @ConfigProperty(name = "auth.keycloak.client-id")
    String clientId;

    @ConfigProperty(name = "auth.keycloak.realm")
    String realm;

    @ConfigProperty(name = "auth.keycloak.admin-username")
    String adminUsername;

    @ConfigProperty(name = "auth.keycloak.admin-password")
    String adminPassword;

    public LoginResponse login(String username, String password) {
        try {
            return passwordGrant(username, password);
        } catch (IncompleteIdentityProfileException incomplete) {
            // Cadastros antigos sem lastName: Keycloak VERIFY_PROFILE bloqueia o grant.
            LOG.infof("Completing incomplete IdP profile for %s", username);
            completeIncompleteKeycloakProfile(username.trim().toLowerCase());
            try {
                return passwordGrant(username, password);
            } catch (IncompleteIdentityProfileException stillIncomplete) {
                throw new IdentityProviderUnavailableException(
                        "Conta no provedor de identidade incompleta (perfil)");
            }
        }
    }

    private LoginResponse passwordGrant(String username, String password) {
        final KeycloakTokenClient.TokenResponse token;
        try {
            token = keycloakTokenClient.passwordGrant("password", clientId, username, password);
        } catch (WebApplicationException e) {
            int status = e.getResponse() != null ? e.getResponse().getStatus() : 0;
            String body = readErrorBody(e);
            LOG.warnf("Keycloak password grant failed status=%d body=%s", status, body);
            if (status == 400 || status == 401) {
                if (body != null && body.contains("Account is not fully set up")) {
                    throw new IncompleteIdentityProfileException();
                }
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
        return toLoginResponse(token);
    }

    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidCredentialsException("Sessão expirada. Faça login novamente.");
        }
        final KeycloakTokenClient.TokenResponse token;
        try {
            token = keycloakTokenClient.refreshGrant("refresh_token", clientId, refreshToken);
        } catch (WebApplicationException e) {
            int status = e.getResponse() != null ? e.getResponse().getStatus() : 0;
            String body = readErrorBody(e);
            LOG.warnf("Keycloak refresh grant failed status=%d body=%s", status, body);
            if (status == 400 || status == 401) {
                throw new InvalidCredentialsException("Sessão expirada. Faça login novamente.");
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
        return toLoginResponse(token);
    }

    /**
     * Revoga o refresh token no Keycloak. Falhas de rede/IdP não impedem o logout local.
     */
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try (Response response = keycloakAdminClient.logout(realm, clientId, refreshToken)) {
            int status = response.getStatus();
            if (status >= 400) {
                LOG.warnf("Keycloak logout returned status=%d", status);
            }
        } catch (WebApplicationException | ProcessingException e) {
            LOG.warnf(e, "Keycloak logout failed — local session still cleared by client");
        }
    }

    private static LoginResponse toLoginResponse(KeycloakTokenClient.TokenResponse token) {
        long expiresIn = token.expiresIn() > 0 ? token.expiresIn() : 300;
        long refreshExpiresIn = token.refreshExpiresIn() > 0 ? token.refreshExpiresIn() : 0;
        String refresh = token.refreshToken() != null ? token.refreshToken() : "";
        return LoginResponse.bearer(token.accessToken(), expiresIn, refresh, refreshExpiresIn);
    }

    private void completeIncompleteKeycloakProfile(String emailOrUsername) {
        String adminToken = fetchAdminToken();
        String authorization = "Bearer " + adminToken;
        List<KeycloakAdminClient.KeycloakUser> users;
        try {
            users = keycloakAdminClient.findUsersByEmail(realm, authorization, emailOrUsername, true);
            if (users == null || users.isEmpty()) {
                users = keycloakAdminClient.findUsersByEmail(realm, authorization, emailOrUsername, false);
            }
        } catch (WebApplicationException | ProcessingException e) {
            throw new IdentityProviderUnavailableException(
                    "Não foi possível completar o perfil no provedor de identidade", e);
        }
        if (users == null || users.isEmpty() || users.get(0).id() == null || users.get(0).id().isBlank()) {
            throw new InvalidCredentialsException("Usuário ou senha inválidos");
        }
        KeycloakAdminClient.KeycloakUser kcUser = users.get(0);
        String email = kcUser.email() != null && !kcUser.email().isBlank()
                ? kcUser.email()
                : emailOrUsername;
        String fallbackName = kcUser.username() != null && !kcUser.username().isBlank()
                ? kcUser.username()
                : email.split("@")[0];
        String name = userRepository.findByEmail(email)
                .map(User::getName)
                .filter(n -> n != null && !n.isBlank())
                .orElse(fallbackName);
        completeKeycloakProfile(authorization, kcUser.id(), email, name);
    }

    /**
     * Indica se o e-mail pode ser usado no cadastro (não existe no Postgres nem no Keycloak).
     */
    public boolean isEmailAvailable(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        if (normalizedEmail.isBlank()) {
            return false;
        }
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            return false;
        }
        String adminToken = fetchAdminToken();
        String authorization = "Bearer " + adminToken;
        try {
            List<KeycloakAdminClient.KeycloakUser> users =
                    keycloakAdminClient.findUsersByEmail(realm, authorization, normalizedEmail, true);
            return users == null || users.isEmpty();
        } catch (WebApplicationException | ProcessingException e) {
            throw new IdentityProviderUnavailableException(
                    "Não foi possível verificar o e-mail no provedor de identidade", e);
        }
    }

    /**
     * Cadastro completo: Keycloak + usuário local com categoria/cidade.
     * Chamado só depois que o app coletou e-mail, nome, senha, perfil e termos.
     */
    @Transactional
    public LoginResponse register(
            String email,
            String password,
            String name,
            Integer category,
            Long cityId
    ) {
        String normalizedEmail = email.trim().toLowerCase();
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Nome é obrigatório no cadastro");
        }
        if (category == null || category < 1 || category > 8) {
            throw new BadRequestException("Categoria inválida");
        }
        if (cityId == null) {
            throw new BadRequestException("Cidade é obrigatória no cadastro");
        }
        City city = cityRepository.findByIdOptional(cityId)
                .orElseThrow(() -> new NotFoundException("Cidade não encontrada"));
        String displayName = name.trim();

        String adminToken = fetchAdminToken();
        try {
            createKeycloakUser(adminToken, normalizedEmail, displayName, password);
        } catch (UserAlreadyExistsException exists) {
            LOG.infof("User already in IdP, attempting resume login for %s", normalizedEmail);
            return resumeInterruptedRegistration(
                    adminToken, normalizedEmail, displayName, password, category, city);
        }

        LoginResponse tokens = login(normalizedEmail, password);
        ensureLocalUser(tokens.getAccessToken(), normalizedEmail, displayName, category, city);
        return tokens;
    }

    /**
     * Conta no Keycloak sem espelho local: completa perfil e autentica com a senha
     * informada. Se a senha não bater, trata como e-mail já cadastrado (sem reset).
     */
    private LoginResponse resumeInterruptedRegistration(
            String adminToken,
            String email,
            String displayName,
            String password,
            Integer category,
            City city
    ) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new UserAlreadyExistsException("Já existe uma conta com este e-mail");
        }

        String authorization = "Bearer " + adminToken;
        List<KeycloakAdminClient.KeycloakUser> users;
        try {
            users = keycloakAdminClient.findUsersByEmail(realm, authorization, email, true);
        } catch (WebApplicationException | ProcessingException e) {
            throw new IdentityProviderUnavailableException(
                    "Não foi possível localizar o usuário no provedor de identidade", e);
        }
        if (users == null || users.isEmpty() || users.get(0).id() == null || users.get(0).id().isBlank()) {
            throw new UserAlreadyExistsException("Já existe uma conta com este e-mail");
        }
        completeKeycloakProfile(authorization, users.get(0).id(), email, displayName);

        final LoginResponse tokens;
        try {
            tokens = login(email, password);
        } catch (InvalidCredentialsException e) {
            throw new UserAlreadyExistsException("Já existe uma conta com este e-mail");
        }
        ensureLocalUser(tokens.getAccessToken(), email, displayName, category, city);
        return tokens;
    }

    private void createKeycloakUser(
            String adminToken,
            String email,
            String displayName,
            String password
    ) {
        String authorization = "Bearer " + adminToken;
        String userId;
        try (Response createResponse = keycloakAdminClient.createUser(
                realm,
                authorization,
                KeycloakAdminClient.CreateUserPayload.of(email, displayName)
        )) {
            int status = createResponse.getStatus();
            if (status == 409) {
                throw new UserAlreadyExistsException("Já existe uma conta com este e-mail");
            }
            if (status != 201 && status != 204) {
                String body = createResponse.hasEntity() ? createResponse.readEntity(String.class) : "";
                LOG.errorf("Keycloak createUser status=%d body=%s", status, body);
                throw new IdentityProviderUnavailableException(
                        "Falha ao criar usuário no provedor de identidade (" + status + ")");
            }
            userId = extractUserId(createResponse);
        } catch (UserAlreadyExistsException e) {
            throw e;
        } catch (WebApplicationException e) {
            int status = e.getResponse() != null ? e.getResponse().getStatus() : 0;
            LOG.warnf("Keycloak createUser exception status=%d body=%s", status, readErrorBody(e));
            if (status == 409) {
                throw new UserAlreadyExistsException("Já existe uma conta com este e-mail");
            }
            throw new IdentityProviderUnavailableException(
                    "Falha ao criar usuário no provedor de identidade", e);
        } catch (ProcessingException e) {
            throw new IdentityProviderUnavailableException(
                    "Não foi possível contactar o provedor de identidade", e);
        }

        if (userId == null || userId.isBlank()) {
            throw new IdentityProviderUnavailableException(
                    "Provedor de identidade não retornou o id do usuário criado");
        }

        applyPassword(authorization, userId, password);
        // Garante firstName/lastName — sem lastName o Keycloak bloqueia o password grant
        // com "Account is not fully set up" (VERIFY_PROFILE).
        completeKeycloakProfile(authorization, userId, email, displayName);
    }

    private void completeKeycloakProfile(
            String authorization,
            String userId,
            String email,
            String displayName
    ) {
        try (Response updateResponse = keycloakAdminClient.updateUser(
                realm,
                userId,
                authorization,
                KeycloakAdminClient.UpdateUserPayload.profile(email, displayName)
        )) {
            int status = updateResponse.getStatus();
            if (status != 204 && status != 200) {
                String body = updateResponse.hasEntity() ? updateResponse.readEntity(String.class) : "";
                LOG.errorf("Keycloak updateUser status=%d body=%s", status, body);
                throw new IdentityProviderUnavailableException(
                        "Falha ao completar perfil no provedor de identidade (" + status + ")");
            }
        } catch (WebApplicationException e) {
            throw new IdentityProviderUnavailableException(
                    "Falha ao completar perfil no provedor de identidade", e);
        } catch (ProcessingException e) {
            throw new IdentityProviderUnavailableException(
                    "Não foi possível completar o perfil no provedor de identidade", e);
        }
    }

    private void applyPassword(String authorization, String userId, String password) {
        try (Response resetResponse = keycloakAdminClient.resetPassword(
                realm,
                userId,
                authorization,
                KeycloakAdminClient.Credential.password(password)
        )) {
            int status = resetResponse.getStatus();
            if (status != 204 && status != 200) {
                String body = resetResponse.hasEntity() ? resetResponse.readEntity(String.class) : "";
                LOG.errorf("Keycloak resetPassword status=%d body=%s", status, body);
                throw new IdentityProviderUnavailableException(
                        "Falha ao definir senha no provedor de identidade (" + status + ")");
            }
        } catch (WebApplicationException e) {
            LOG.warnf("Keycloak resetPassword exception status=%d body=%s",
                    e.getResponse() != null ? e.getResponse().getStatus() : 0,
                    readErrorBody(e));
            throw new IdentityProviderUnavailableException(
                    "Falha ao definir senha no provedor de identidade", e);
        } catch (ProcessingException e) {
            throw new IdentityProviderUnavailableException(
                    "Não foi possível definir a senha no provedor de identidade", e);
        }
    }

    private void ensureLocalUser(
            String accessToken,
            String email,
            String name,
            Integer category,
            City city
    ) {
        String keycloakId = extractJwtSubject(accessToken);
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new IdentityProviderUnavailableException(
                    "Token do provedor de identidade sem subject — cadastro não espelhado");
        }
        var existing = userRepository.findByKeycloakId(keycloakId);
        if (existing.isPresent()) {
            User user = existing.get();
            user.setName(name);
            user.setCategory(category);
            user.setCity(city);
            return;
        }
        User user = new User();
        user.setKeycloakId(keycloakId);
        user.setEmail(email);
        user.setName(name);
        user.setCategory(category);
        user.setCity(city);
        userRepository.persist(user);
        LOG.infof("Local user created id=%s email=%s", keycloakId, email);
    }

    private String fetchAdminToken() {
        try {
            KeycloakAdminClient.AdminTokenResponse token = keycloakAdminClient.adminPasswordGrant(
                    "password",
                    "admin-cli",
                    adminUsername,
                    adminPassword
            );
            if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
                throw new IdentityProviderUnavailableException(
                        "Admin do provedor de identidade não retornou access_token");
            }
            return token.accessToken();
        } catch (WebApplicationException | ProcessingException e) {
            throw new IdentityProviderUnavailableException(
                    "Não foi possível autenticar no admin do provedor de identidade", e);
        }
    }

    private static String extractUserId(Response response) {
        String location = response.getHeaderString("Location");
        if (location == null || location.isBlank()) {
            return null;
        }
        int slash = location.lastIndexOf('/');
        if (slash < 0 || slash == location.length() - 1) {
            return null;
        }
        return location.substring(slash + 1);
    }

    private static String extractJwtSubject(String accessToken) {
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            int key = json.indexOf("\"sub\"");
            if (key < 0) {
                return null;
            }
            int colon = json.indexOf(':', key);
            int firstQuote = json.indexOf('"', colon + 1);
            int secondQuote = json.indexOf('"', firstQuote + 1);
            if (firstQuote < 0 || secondQuote < 0) {
                return null;
            }
            return json.substring(firstQuote + 1, secondQuote);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String readErrorBody(WebApplicationException e) {
        try {
            if (e.getResponse() != null && e.getResponse().hasEntity()) {
                return e.getResponse().readEntity(String.class);
            }
        } catch (RuntimeException ignored) {
            // ignore
        }
        return "";
    }
}
