package br.com.stringtracker.service;

import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.UserRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import org.eclipse.microprofile.jwt.JsonWebToken;

@ApplicationScoped
public class CurrentUserService {

    @Inject
    JsonWebToken jwt;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    UserRepository userRepository;

    @Transactional
    public User requireCurrentUser() {
        final TokenProfile profile;
        try {
            profile = TokenProfile.from(jwt, securityIdentity);
        } catch (IllegalArgumentException ex) {
            throw new NotAuthorizedException("Missing subject in token");
        }

        User user = userRepository.findByKeycloakId(profile.keycloakId())
                .orElseGet(() -> createFromProfile(profile));
        if (!user.isActive()) {
            throw new ForbiddenException("Conta desativada");
        }
        return user;
    }

    private User createFromProfile(TokenProfile profile) {
        User user = new User();
        user.setKeycloakId(profile.keycloakId());
        user.setEmail(profile.email());
        user.setName(profile.name());
        userRepository.persist(user);
        return user;
    }
}
