package br.com.stringtracker.service;

import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.UserRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class CurrentUserServiceTest {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    UserRepository userRepository;

    @Test
    @TestSecurity(user = "kc-jit-1")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "kc-jit-1"),
            @Claim(key = "email", value = "jit@example.com"),
            @Claim(key = "name", value = "JIT Player")
    })
    void requireCurrentUser_createsLocalUserFromTokenOnFirstCall() {
        User user = currentUserService.requireCurrentUser();

        assertNotNull(user.getId());
        assertEquals("kc-jit-1", user.getKeycloakId());
        assertFalse(user.isPremium());
        assertNotNull(user.getEmail());
        assertFalse(user.getEmail().isBlank());
        assertNotNull(user.getName());
        assertFalse(user.getName().isBlank());
        assertTrue(userRepository.findByKeycloakId("kc-jit-1").isPresent());
    }

    @Test
    @TestSecurity(user = "kc-jit-2")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "kc-jit-2"),
            @Claim(key = "email", value = "again@example.com"),
            @Claim(key = "name", value = "Again")
    })
    @Transactional
    void requireCurrentUser_reusesExistingUser() {
        User first = currentUserService.requireCurrentUser();
        User second = currentUserService.requireCurrentUser();

        assertEquals(first.getId(), second.getId());
        assertEquals(1, userRepository.count("keycloakId", "kc-jit-2"));
    }

    @Test
    @TestSecurity(user = "kc-premium-jit", roles = "premium")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "kc-premium-jit"),
            @Claim(key = "email", value = "prem@example.com"),
            @Claim(key = "name", value = "Prem")
    })
    void requireCurrentUser_syncsPremiumFromTokenRole() {
        User user = currentUserService.requireCurrentUser();
        assertTrue(user.isPremium());
    }
}
