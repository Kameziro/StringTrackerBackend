package br.com.stringtracker.service;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubAdmin;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.ClubAdminRepository;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.repository.CoachRepository;
import br.com.stringtracker.repository.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ClubAccessServiceTest {

    private static final String PLATFORM = "kc-access-platform";
    private static final String ADMIN_A = "kc-access-admin-a";
    private static final String COACH = "kc-access-coach";
    private static final String COMMON = "kc-access-common";
    private static final String FORMER_ADMIN = "kc-access-former-admin";
    private static final String FORMER_COACH = "kc-access-former-coach";

    @Inject
    ClubAccessService access;

    @Inject
    UserRepository userRepository;

    @Inject
    ClubRepository clubRepository;

    @Inject
    ClubAdminRepository clubAdminRepository;

    @Inject
    CoachRepository coachRepository;

    private long clubAId;
    private long clubBId;
    private long coachId;
    private long formerCoachId;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            User platform = user(PLATFORM);
            platform.setPlatformAdmin(true);
            User adminA = user(ADMIN_A);
            User coachUser = user(COACH);
            user(COMMON);
            User formerAdmin = user(FORMER_ADMIN);
            User formerCoach = user(FORMER_COACH);

            Club clubA = clubRepository.findOrCreateByName("Acesso Clube A");
            Club clubB = clubRepository.findOrCreateByName("Acesso Clube B");
            clubAId = clubA.getId();
            clubBId = clubB.getId();

            if (clubAdminRepository.count("club = ?1 and user = ?2", clubA, adminA) == 0) {
                clubAdminRepository.persist(ClubAdmin.create(clubA, adminA));
            }
            Coach coach = coachRepository.find("user", coachUser).firstResultOptional()
                    .orElseGet(() -> {
                        Coach created = Coach.create(coachUser);
                        coachRepository.persist(created);
                        return created;
                    });
            coachId = coach.getId();

            if (clubAdminRepository.count("club = ?1 and user = ?2", clubA, formerAdmin) == 0) {
                ClubAdmin link = ClubAdmin.create(clubA, formerAdmin);
                link.markExcluded();
                clubAdminRepository.persist(link);
            }
            Coach former = coachRepository.find("user", formerCoach).firstResultOptional()
                    .orElseGet(() -> {
                        Coach created = Coach.create(formerCoach);
                        created.markExcluded();
                        coachRepository.persist(created);
                        return created;
                    });
            formerCoachId = former.getId();
        });
    }

    private User user(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId).orElseGet(() -> {
            User user = new User();
            user.setKeycloakId(keycloakId);
            user.setName(keycloakId);
            user.setEmail(keycloakId + "@example.com");
            userRepository.persist(user);
            return user;
        });
    }

    @Test
    @TestSecurity(user = PLATFORM)
    @JwtSecurity(claims = {@Claim(key = "sub", value = PLATFORM)})
    void platformAdmin_isAllowedInThePlatformArea() {
        assertDoesNotThrow(() -> access.requirePlatformAdmin());
    }

    @Test
    @TestSecurity(user = COMMON)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COMMON)})
    void commonUser_isDeniedInThePlatformArea() {
        assertThrows(ForbiddenException.class, () -> access.requirePlatformAdmin());
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void clubAdmin_isDeniedInThePlatformArea() {
        assertThrows(ForbiddenException.class, () -> access.requirePlatformAdmin());
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void clubAdmin_isAllowedInTheirOwnClub() {
        assertDoesNotThrow(() -> access.requireClubAdmin(clubAId));
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void clubAdmin_isDeniedInAnotherClub() {
        assertThrows(ForbiddenException.class, () -> access.requireClubAdmin(clubBId));
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void coach_isDeniedAsClubAdmin() {
        assertThrows(ForbiddenException.class, () -> access.requireClubAdmin(clubAId));
    }

    @Test
    @TestSecurity(user = COMMON)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COMMON)})
    void commonUser_isDeniedAsClubAdmin() {
        assertThrows(ForbiddenException.class, () -> access.requireClubAdmin(clubAId));
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void coach_isAllowedOnTheirOwnProfile() {
        assertDoesNotThrow(() -> access.requireCoach(coachId));
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void someoneElse_isDeniedOnACoachProfile() {
        assertThrows(ForbiddenException.class, () -> access.requireCoach(coachId));
    }

    @Test
    @TestSecurity(user = COMMON)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COMMON)})
    void commonUser_isDeniedOnACoachProfile() {
        assertThrows(ForbiddenException.class, () -> access.requireCoach(coachId));
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void adminClubIds_listsOnlyTheClubsTheUserAdministers() {
        assertEquals(Set.of(clubAId), access.adminClubIds());
    }

    @Test
    @TestSecurity(user = COMMON)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COMMON)})
    void adminClubIds_isEmptyForACommonUser() {
        assertTrue(access.adminClubIds().isEmpty());
    }

    @Test
    @TestSecurity(user = FORMER_ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = FORMER_ADMIN)})
    void clubAdmin_whoseLinkWasDeactivated_isDenied() {
        assertThrows(ForbiddenException.class, () -> access.requireClubAdmin(clubAId));
        assertTrue(access.adminClubIds().isEmpty());
    }

    @Test
    @TestSecurity(user = FORMER_COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = FORMER_COACH)})
    void coach_whoseProfileWasDeactivated_isDenied() {
        assertThrows(ForbiddenException.class, () -> access.requireCoach(formerCoachId));
    }
}
