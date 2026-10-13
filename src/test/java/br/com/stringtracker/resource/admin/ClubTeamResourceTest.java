package br.com.stringtracker.resource.admin;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.Invite;
import br.com.stringtracker.model.InviteKind;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.InviteRepository;
import br.com.stringtracker.service.ClockProducer;
import br.com.stringtracker.support.ScheduleFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class ClubTeamResourceTest {

    private static final String ADMIN = "kc-team-admin";
    private static final String SECOND_ADMIN = "kc-team-second-admin";
    private static final String OTHER_ADMIN = "kc-team-other-admin";
    private static final String COMMON = "kc-team-common";
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    InviteRepository inviteRepository;

    private long clubId;
    private long otherClubId;
    private long coachAnaId;
    private long coachBrunoId;

    /**
     * O clube tem dois admins (Alice e Bruno de Admin), a professora Ana e o professor Bruno, e convites: um de admin
     * válido, um de admin vencido, um de admin aceito, um de professor válido e um de admin desativado. O outro clube
     * tem um convite de admin e um admin próprios.
     */
    @BeforeEach
    void seed() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
        QuarkusTransaction.requiringNew().run(() -> {
            Club club = fixtures.club("Time Clube");
            Club other = fixtures.club("Time Outro");
            User alice = fixtures.user(ADMIN);
            alice.setName("Alice Admin");
            User bruno = fixtures.user(SECOND_ADMIN);
            bruno.setName("Bruno Admin");
            fixtures.admin(club, alice);
            fixtures.admin(club, bruno);
            fixtures.admin(other, fixtures.user(OTHER_ADMIN));
            fixtures.user(COMMON);

            User anaUser = fixtures.user("kc-team-coach-ana-" + UUID.randomUUID());
            anaUser.setName("Ana Professora");
            anaUser.setAvatarUrl("/api/media/users/1/avatar.png");
            Coach ana = fixtures.coach(anaUser);
            ana.setBio("Bandeja");
            ana.setOffersGroup(true);
            User brunoCoachUser = fixtures.user("kc-team-coach-bruno-" + UUID.randomUUID());
            brunoCoachUser.setName("Bruno Professor");
            Coach brunoCoach = fixtures.coach(brunoCoachUser);
            brunoCoach.setOffersDoubles(false);
            fixtures.link(club, ana, 9000L, 12000L).setPriceGroupCents(5000L);
            fixtures.link(club, brunoCoach, null, null);
            ClubCoach gone = fixtures.link(club, fixtures.coach(), 1000L, 1000L);
            gone.setActive(false);

            invite(club, "valido@example.com", InviteKind.CLUB_ADMIN, NOW.plus(Duration.ofDays(3)), false, true);
            invite(club, "vencido@example.com", InviteKind.CLUB_ADMIN, NOW.minus(Duration.ofDays(1)), false, true);
            invite(club, "aceito@example.com", InviteKind.CLUB_ADMIN, NOW.plus(Duration.ofDays(3)), true, true);
            invite(club, "desativado@example.com", InviteKind.CLUB_ADMIN, NOW.plus(Duration.ofDays(3)), false, false);
            invite(club, "professor@example.com", InviteKind.COACH, NOW.plus(Duration.ofDays(5)), false, true);
            invite(other, "outro@example.com", InviteKind.CLUB_ADMIN, NOW.plus(Duration.ofDays(3)), false, true);
            clubId = club.getId();
            otherClubId = other.getId();
            coachAnaId = ana.getId();
            coachBrunoId = brunoCoach.getId();
        });
    }

    private void invite(Club club, String email, InviteKind kind, Instant expiresAt, boolean accepted, boolean active) {
        Invite invite = Invite.create(UUID.randomUUID().toString().replace("-", "") + "0".repeat(32), email, kind, club,
                null, expiresAt);
        if (accepted) {
            invite.setAcceptedAt(NOW);
        }
        if (!active) {
            invite.markExcluded();
        }
        inviteRepository.persist(invite);
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void listsTheClubsAdminsByName_andOnlyTheirPendingAdminInvites() {
        given().when().get("/api/admin/clubs/" + clubId + "/admins")
                .then().statusCode(200)
                .body("admins.name", contains("Alice Admin", "Bruno Admin"))
                .body("admins[0].email", equalTo(ADMIN + "@example.com"))
                .body("pendingInvites.email", contains("vencido@example.com", "valido@example.com"))
                .body("pendingInvites.find { it.email == 'valido@example.com' }.expired", equalTo(false))
                .body("pendingInvites.find { it.email == 'valido@example.com' }.kind", equalTo("CLUB_ADMIN"))
                .body("pendingInvites.find { it.email == 'valido@example.com' }.expiresAt",
                        equalTo("2026-10-08T12:00:00Z"))
                .body("pendingInvites.find { it.email == 'vencido@example.com' }.expired", equalTo(true));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void pendingInvitesNeverExposeTheTokenOrItsHash() {
        given().when().get("/api/admin/clubs/" + clubId + "/admins")
                .then().statusCode(200)
                .body("pendingInvites[0]", not(hasKey("link")))
                .body("pendingInvites[0]", not(hasKey("token")))
                .body("pendingInvites[0]", not(hasKey("tokenHash")));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void listsTheClubsCoachesWithOffersAndPrices_andTheirPendingInvites() {
        given().when().get("/api/admin/clubs/" + clubId + "/coaches")
                .then().statusCode(200)
                .body("coaches.name", contains("Ana Professora", "Bruno Professor"))
                .body("coaches[0].coachId", equalTo((int) coachAnaId))
                .body("coaches[0].email", org.hamcrest.Matchers.startsWith("kc-team-coach-ana-"))
                .body("coaches[0].avatarUrl", equalTo("/api/media/users/1/avatar.png"))
                .body("coaches[0].bio", equalTo("Bandeja"))
                .body("coaches[0].offers.singles", equalTo(true))
                .body("coaches[0].offers.doubles", equalTo(true))
                .body("coaches[0].offers.group", equalTo(true))
                .body("coaches[0].prices.singlesCents", equalTo(9000))
                .body("coaches[0].prices.doublesCents", equalTo(12000))
                .body("coaches[0].prices.groupCents", equalTo(5000))
                .body("coaches[1].coachId", equalTo((int) coachBrunoId))
                .body("coaches[1].offers.doubles", equalTo(false))
                .body("coaches[1].prices.singlesCents", nullValue())
                .body("pendingInvites.email", contains("professor@example.com"))
                .body("pendingInvites[0].kind", equalTo("COACH"));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void coachesOfAClubWithNobodyLinked_isEmpty() {
        long empty = QuarkusTransaction.requiringNew().call(() -> {
            Club club = fixtures.club("Time Vazio");
            fixtures.admin(club, fixtures.user(ADMIN));
            return club.getId();
        });

        given().when().get("/api/admin/clubs/" + empty + "/coaches")
                .then().statusCode(200).body("coaches", empty()).body("pendingInvites", empty());
    }

    @Test
    @TestSecurity(user = OTHER_ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = OTHER_ADMIN)})
    void adminOfAnotherClub_gets403OnBothLists() {
        given().when().get("/api/admin/clubs/" + clubId + "/admins").then().statusCode(403);
        given().when().get("/api/admin/clubs/" + clubId + "/coaches").then().statusCode(403);
        given().when().get("/api/admin/clubs/" + otherClubId + "/admins")
                .then().statusCode(200).body("pendingInvites.email", contains("outro@example.com"));
    }

    @Test
    @TestSecurity(user = COMMON)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COMMON)})
    void userWhoIsNotAnAdmin_gets403() {
        given().when().get("/api/admin/clubs/" + clubId + "/admins").then().statusCode(403);
        given().when().get("/api/admin/clubs/" + clubId + "/coaches").then().statusCode(403);
    }
}
