package br.com.stringtracker.service;

import br.com.stringtracker.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FreemiumPolicyTest {

    @Test
    void freeUser_withZeroRackets_mayCreate() {
        User free = freeUser();
        assertDoesNotThrow(() -> FreemiumPolicy.assertCanCreateRacket(free, 0));
    }

    @Test
    void freeUser_withOneRacket_isBlocked() {
        User free = freeUser();
        assertThrows(FreeTierLimitExceededException.class,
                () -> FreemiumPolicy.assertCanCreateRacket(free, 1));
    }

    @Test
    void premiumUser_withManyRackets_mayCreate() {
        User premium = freeUser();
        premium.setPremium(true);
        assertDoesNotThrow(() -> FreemiumPolicy.assertCanCreateRacket(premium, 5));
    }

    private static User freeUser() {
        User user = new User();
        user.setKeycloakId("kc");
        user.setEmail("a@b.c");
        user.setName("n");
        user.setPremium(false);
        return user;
    }
}
