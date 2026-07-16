package br.com.stringtracker.service;

import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;

public record TokenProfile(String keycloakId, String email, String name) {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_PREFERRED_USERNAME = "preferred_username";

    public static TokenProfile from(JsonWebToken jwt, SecurityIdentity identity) {
        String keycloakId = resolveSubject(jwt, identity);
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new IllegalArgumentException("JWT subject (sub) is required");
        }
        return new TokenProfile(
                keycloakId,
                resolveEmail(jwt, keycloakId),
                resolveName(jwt, keycloakId)
        );
    }

    private static String resolveSubject(JsonWebToken jwt, SecurityIdentity identity) {
        String subject = jwt.getSubject();
        if (subject != null && !subject.isBlank()) {
            return subject;
        }
        String claimSub = claimAsString(jwt, "sub");
        if (claimSub != null && !claimSub.isBlank()) {
            return claimSub;
        }
        String principalName = jwt.getName();
        if (principalName != null && !principalName.isBlank()) {
            return principalName;
        }
        if (!identity.isAnonymous()) {
            return identity.getPrincipal().getName();
        }
        return null;
    }

    private static String resolveEmail(JsonWebToken jwt, String keycloakId) {
        String email = claimAsString(jwt, CLAIM_EMAIL);
        if (email != null && !email.isBlank()) {
            return email;
        }
        return keycloakId + "@users.stringtracker.local";
    }

    private static String resolveName(JsonWebToken jwt, String keycloakId) {
        String name = claimAsString(jwt, CLAIM_NAME);
        if (name != null && !name.isBlank()) {
            return name;
        }
        String preferred = claimAsString(jwt, CLAIM_PREFERRED_USERNAME);
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return keycloakId;
    }

    private static String claimAsString(JsonWebToken jwt, String name) {
        Object value = jwt.getClaim(name);
        return value == null ? null : String.valueOf(value);
    }
}
