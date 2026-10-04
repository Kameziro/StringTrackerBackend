package br.com.stringtracker.model;

import br.com.stringtracker.repository.ClubAdminRepository;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.repository.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Documenta que consultas comuns enxergam registros soft-deleted: o filtro `active` é sempre explícito. */
@QuarkusTest
class BaseEntityQueryTest {

    @Inject
    UserRepository userRepository;

    @Inject
    ClubRepository clubRepository;

    @Inject
    ClubAdminRepository clubAdminRepository;

    @Test
    void plainQueries_stillSeeSoftDeletedRows_soActiveMustBeFilteredExplicitly() {
        String key = "kc-basequery-" + UUID.randomUUID();
        long[] ids = QuarkusTransaction.requiringNew().call(() -> {
            User user = new User();
            user.setKeycloakId(key);
            user.setName(key);
            user.setEmail(key + "@example.com");
            userRepository.persist(user);
            Club club = Club.create("Soft delete " + key);
            clubRepository.persist(club);
            ClubAdmin link = ClubAdmin.create(club, user);
            link.markExcluded();
            clubAdminRepository.persist(link);
            return new long[]{club.getId(), user.getId()};
        });

        assertEquals(1, clubAdminRepository.count("user.id", ids[1]));
        assertTrue(clubAdminRepository.findLink(ids[0], ids[1]).isPresent());
        assertFalse(clubAdminRepository.isAdmin(ids[0], ids[1]));
    }
}
