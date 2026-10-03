package br.com.stringtracker.service;

import br.com.stringtracker.dto.InviteAcceptedResponse;
import br.com.stringtracker.dto.InviteInfoResponse;
import br.com.stringtracker.dto.InviteResponse;
import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.Invite;
import br.com.stringtracker.model.InviteKind;
import br.com.stringtracker.repository.ClubAdminRepository;
import br.com.stringtracker.repository.ClubCoachRepository;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.repository.CoachRepository;
import br.com.stringtracker.repository.InviteRepository;
import br.com.stringtracker.repository.UserRepository;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class InviteServiceTest {

    private static final String INVITER = "kc-invite-inviter";
    private static final String GUEST = "kc-invite-guest";
    private static final String GUEST_EMAIL = "guest.invite@example.com";
    private static final String LINK_PREFIX = "http://localhost:3100/convite/";

    @Inject
    InviteService invites;

    @Inject
    InviteRepository inviteRepository;

    @Inject
    ClubRepository clubRepository;

    @Inject
    ClubAdminRepository clubAdminRepository;

    @Inject
    CoachRepository coachRepository;

    @Inject
    ClubCoachRepository clubCoachRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
    }

    @Test
    @TestSecurity(user = INVITER)
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = INVITER),
            @Claim(key = "email", value = "inviter.invite@example.com"),
            @Claim(key = "name", value = "Inviter")
    })
    void inviteClubAdmin_storesOnlyTheHash_emailsTheLink_andExpiresInSevenDays() {
        long clubId = newClub();
        String email = unique("admin") + "@example.com";

        InviteResponse response = invites.inviteClubAdmin(clubId, email);

        assertEquals(email, response.email());
        assertEquals(InviteKind.CLUB_ADMIN, response.kind());
        assertTrue(response.link().startsWith(LINK_PREFIX));
        String token = tokenOf(response.link());

        Invite stored = findByHash(sha256Hex(token));
        assertNotNull(stored);
        assertNotEquals(token, stored.getTokenHash());
        assertEquals(64, stored.getTokenHash().length());
        assertEquals(email, stored.getEmail());
        assertEquals(InviteKind.CLUB_ADMIN, stored.getKind());
        assertEquals(clubId, stored.getClub().getId());
        assertEquals(INVITER, stored.getInvitedBy().getKeycloakId());
        assertWithinMinute(Instant.now().plus(Duration.ofDays(7)), stored.getExpiresAt());
        assertWithinMinute(stored.getExpiresAt(), response.expiresAt());

        List<Mail> sent = mailbox.getMailsSentTo(email);
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).getText().contains(response.link()));
    }

    @Test
    @TestSecurity(user = INVITER)
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = INVITER),
            @Claim(key = "email", value = "inviter.invite@example.com"),
            @Claim(key = "name", value = "Inviter")
    })
    void inviteCoach_createsACoachInviteAndEmailsIt() {
        long clubId = newClub();
        String email = unique("coach") + "@example.com";

        InviteResponse response = invites.inviteCoach(clubId, email);

        assertEquals(InviteKind.COACH, response.kind());
        assertEquals(InviteKind.COACH, findByHash(sha256Hex(tokenOf(response.link()))).getKind());
        assertEquals(1, mailbox.getMailsSentTo(email).size());
    }

    @Test
    @TestSecurity(user = INVITER)
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = INVITER),
            @Claim(key = "email", value = "inviter.invite@example.com"),
            @Claim(key = "name", value = "Inviter")
    })
    void resendingAnInvite_issuesANewTokenAndInvalidatesThePreviousOne() {
        long clubId = newClub();
        String email = unique("resend") + "@example.com";

        String oldToken = tokenOf(invites.inviteClubAdmin(clubId, email).link());
        String newToken = tokenOf(invites.inviteClubAdmin(clubId, email).link());

        assertNotEquals(oldToken, newToken);
        assertThrows(NotFoundException.class, () -> invites.describe(oldToken));
        assertEquals(clubId, invites.describe(newToken).clubId());
        assertEquals(2, mailbox.getMailsSentTo(email).size());
    }

    @Test
    @TestSecurity(user = GUEST)
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = GUEST),
            @Claim(key = "email", value = GUEST_EMAIL),
            @Claim(key = "name", value = "Guest")
    })
    void describe_returnsTheClubAndKindOfAValidInvite() {
        long clubId = newClub();
        String token = seedInvite(InviteKind.CLUB_ADMIN, clubId, "other@example.com", Instant.now().plus(Duration.ofDays(1)));

        InviteInfoResponse info = invites.describe(token);

        assertEquals(InviteKind.CLUB_ADMIN, info.kind());
        assertEquals(clubId, info.clubId());
        assertEquals(clubRepository.findById(clubId).getName(), info.clubName());
    }

    @Test
    @TestSecurity(user = GUEST)
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = GUEST),
            @Claim(key = "email", value = GUEST_EMAIL),
            @Claim(key = "name", value = "Guest")
    })
    void acceptClubAdminInvite_withAnotherEmail_makesTheUserAdminAndRecordsWhoAccepted() {
        long clubId = newClub();
        String token = seedInvite(InviteKind.CLUB_ADMIN, clubId, "invited.address@example.com", Instant.now().plus(Duration.ofDays(1)));

        InviteAcceptedResponse accepted = invites.accept(token);

        assertEquals(InviteKind.CLUB_ADMIN, accepted.kind());
        assertEquals(clubId, accepted.clubId());
        long guestId = userRepository.findByKeycloakId(GUEST).orElseThrow().getId();
        assertTrue(clubAdminRepository.isAdmin(clubId, guestId));
        Invite stored = findByHash(sha256Hex(token));
        assertNotNull(stored.getAcceptedAt());
        assertEquals(GUEST, stored.getAcceptedBy().getKeycloakId());
    }

    @Test
    @TestSecurity(user = GUEST)
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = GUEST),
            @Claim(key = "email", value = GUEST_EMAIL),
            @Claim(key = "name", value = "Guest")
    })
    void acceptCoachInvite_createsTheCoachProfileAndLinksItToTheClub() {
        long clubId = newClub();
        String token = seedInvite(InviteKind.COACH, clubId, GUEST_EMAIL, Instant.now().plus(Duration.ofDays(1)));

        InviteAcceptedResponse accepted = invites.accept(token);

        assertEquals(InviteKind.COACH, accepted.kind());
        long guestId = userRepository.findByKeycloakId(GUEST).orElseThrow().getId();
        var coach = coachRepository.findByUserId(guestId).orElseThrow();
        assertTrue(clubCoachRepository.findByClubAndCoach(clubId, coach.getId()).isPresent());
    }

    @Test
    @TestSecurity(user = GUEST)
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = GUEST),
            @Claim(key = "email", value = GUEST_EMAIL),
            @Claim(key = "name", value = "Guest")
    })
    void acceptCoachInvite_forACoachAtAnotherClub_reusesTheSameCoachProfile() {
        long clubA = newClub();
        long clubB = newClub();

        invites.accept(seedInvite(InviteKind.COACH, clubA, GUEST_EMAIL, Instant.now().plus(Duration.ofDays(1))));
        long guestId = userRepository.findByKeycloakId(GUEST).orElseThrow().getId();
        long coachIdAfterFirst = coachRepository.findByUserId(guestId).orElseThrow().getId();
        invites.accept(seedInvite(InviteKind.COACH, clubB, GUEST_EMAIL, Instant.now().plus(Duration.ofDays(1))));

        assertEquals(1, coachRepository.count("user.id", guestId));
        assertEquals(coachIdAfterFirst, coachRepository.findByUserId(guestId).orElseThrow().getId());
        assertTrue(clubCoachRepository.findByClubAndCoach(clubA, coachIdAfterFirst).isPresent());
        assertTrue(clubCoachRepository.findByClubAndCoach(clubB, coachIdAfterFirst).isPresent());
    }

    @Test
    @TestSecurity(user = GUEST)
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = GUEST),
            @Claim(key = "email", value = GUEST_EMAIL),
            @Claim(key = "name", value = "Guest")
    })
    void acceptingAnExpiredInvite_isRefusedAndLinksNothing() {
        long clubId = newClub();
        String token = seedInvite(InviteKind.CLUB_ADMIN, clubId, GUEST_EMAIL, Instant.now().minus(Duration.ofMinutes(1)));

        InviteUnavailableException error = assertThrows(InviteUnavailableException.class, () -> invites.accept(token));

        assertEquals("Convite expirado. Peça um novo ao clube", error.getMessage());
        long guestId = userRepository.findByKeycloakId(GUEST).orElseThrow().getId();
        assertFalse(clubAdminRepository.isAdmin(clubId, guestId));
    }

    @Test
    @TestSecurity(user = GUEST)
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = GUEST),
            @Claim(key = "email", value = GUEST_EMAIL),
            @Claim(key = "name", value = "Guest")
    })
    void acceptingTheSameInviteTwice_isRefusedTheSecondTime() {
        long clubId = newClub();
        String token = seedInvite(InviteKind.CLUB_ADMIN, clubId, GUEST_EMAIL, Instant.now().plus(Duration.ofDays(1)));
        invites.accept(token);

        InviteUnavailableException error = assertThrows(InviteUnavailableException.class, () -> invites.accept(token));

        assertEquals("Este convite já foi aceito", error.getMessage());
    }

    @Test
    @TestSecurity(user = GUEST)
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = GUEST),
            @Claim(key = "email", value = GUEST_EMAIL),
            @Claim(key = "name", value = "Guest")
    })
    void unknownToken_isNotFound() {
        assertThrows(NotFoundException.class, () -> invites.describe("token-that-was-never-issued"));
        assertThrows(NotFoundException.class, () -> invites.accept("token-that-was-never-issued"));
    }

    private long newClub() {
        return QuarkusTransaction.requiringNew().call(() -> {
            Club club = Club.create(unique("Convite Clube"));
            clubRepository.persist(club);
            return club.getId();
        });
    }

    /** Grava um convite direto no banco e devolve o token cru (o banco guarda só o hash). */
    private String seedInvite(InviteKind kind, long clubId, String email, Instant expiresAt) {
        String token = unique("tok");
        QuarkusTransaction.requiringNew().run(() -> {
            Club club = clubRepository.findById(clubId);
            inviteRepository.persist(Invite.create(sha256Hex(token), email, kind, club, null, expiresAt));
        });
        return token;
    }

    private Invite findByHash(String hash) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Invite invite = inviteRepository.findByTokenHash(hash).orElseThrow();
            invite.getClub().getId();
            if (invite.getInvitedBy() != null) {
                invite.getInvitedBy().getKeycloakId();
            }
            if (invite.getAcceptedBy() != null) {
                invite.getAcceptedBy().getKeycloakId();
            }
            return invite;
        });
    }

    private static String tokenOf(String link) {
        return link.substring(LINK_PREFIX.length());
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static void assertWithinMinute(Instant expected, Instant actual) {
        assertTrue(Duration.between(expected, actual).abs().compareTo(Duration.ofMinutes(1)) < 0,
                "expected " + expected + " but was " + actual);
    }
}
