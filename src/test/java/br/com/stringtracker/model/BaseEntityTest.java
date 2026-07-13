package br.com.stringtracker.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseEntityTest {

    @Test
    void markExcluded_deactivatesAndSetsExclusionDate() {
        User user = new User();
        assertTrue(user.isActive());

        user.markExcluded();

        assertFalse(user.isActive());
        assertNotNull(user.getExclusionDate());
    }
}
