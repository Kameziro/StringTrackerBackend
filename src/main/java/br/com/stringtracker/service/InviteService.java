package br.com.stringtracker.service;

import br.com.stringtracker.dto.InviteAcceptedResponse;
import br.com.stringtracker.dto.InviteInfoResponse;
import br.com.stringtracker.dto.InviteRegisterRequest;
import br.com.stringtracker.dto.InviteResponse;
import br.com.stringtracker.dto.LoginResponse;
import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubAdmin;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.Invite;
import br.com.stringtracker.model.InviteKind;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.ClubAdminRepository;
import br.com.stringtracker.repository.ClubCoachRepository;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.repository.CoachRepository;
import br.com.stringtracker.repository.InviteRepository;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Convites de admin e de professor por e-mail. O token tem 32 bytes aleatórios (Base64 URL-safe)
 * e só o hash SHA-256 fica no banco. Reemitir um convite para o mesmo clube, e-mail e tipo
 * invalida o anterior. Quem aceita não precisa ter o e-mail convidado; o aceite registra {@code accepted_by}.
 */
@ApplicationScoped
public class InviteService {

    private static final Duration VALIDITY = Duration.ofDays(7);
    private static final int TOKEN_BYTES = 32;

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
    CurrentUserService currentUserService;

    @Inject
    AuthService authService;

    @Inject
    Mailer mailer;

    @ConfigProperty(name = "invite.club-admin-url-template")
    String clubAdminUrlTemplate;

    @ConfigProperty(name = "invite.coach-url-template")
    String coachUrlTemplate;

    private final SecureRandom random = new SecureRandom();

    @Transactional
    public InviteResponse inviteClubAdmin(long clubId, String email) {
        return issue(clubId, email, InviteKind.CLUB_ADMIN, clubAdminUrlTemplate);
    }

    @Transactional
    public InviteResponse inviteCoach(long clubId, String email) {
        return issue(clubId, email, InviteKind.COACH, coachUrlTemplate);
    }

    @Transactional
    public InviteInfoResponse describe(String token) {
        Invite invite = requireUsable(orNotFound(inviteRepository.findByTokenHash(hash(token))));
        Club club = invite.getClub();
        return new InviteInfoResponse(invite.getKind(), club.getId(), club.getName(), invite.getExpiresAt());
    }

    @Transactional
    public InviteAcceptedResponse accept(String token) {
        User user = currentUserService.requireCurrentUser();
        return complete(lockUsable(token), user);
    }

    /**
     * Cadastro e aceite de uma vez, para quem ainda não tem conta. A conta nasce com o e-mail do convite
     * (nunca um valor do cliente) e o convite é validado e travado antes de qualquer chamada ao Keycloak,
     * então token inválido não cria conta. O Keycloak fica fora da transação: se o aceite falhar depois
     * da conta criada lá, repetir com a mesma senha retoma o cadastro, como no cadastro do app.
     */
    @Transactional
    public LoginResponse registerAndAccept(String token, InviteRegisterRequest request) {
        Invite invite = lockUsable(token);
        AuthService.Registration registration = authService.registerInvitee(
                invite.getEmail(), request.password(), request.name(), request.category(), request.cityId());
        complete(invite, registration.user());
        return registration.tokens();
    }

    private Invite lockUsable(String token) {
        return requireUsable(orNotFound(inviteRepository.findByTokenHashForUpdate(hash(token))));
    }

    private InviteAcceptedResponse complete(Invite invite, User user) {
        Club club = invite.getClub();
        switch (invite.getKind()) {
            case CLUB_ADMIN -> linkAdmin(club, user);
            case COACH -> linkCoach(club, user);
        }
        invite.setAcceptedAt(Instant.now());
        invite.setAcceptedBy(user);
        return new InviteAcceptedResponse(invite.getKind(), club.getId(), club.getName());
    }

    private InviteResponse issue(long clubId, String rawEmail, InviteKind kind, String urlTemplate) {
        Club club = clubRepository.findActiveById(clubId)
                .orElseThrow(() -> new NotFoundException("Clube não encontrado"));
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        inviteRepository.findPending(clubId, email, kind).forEach(Invite::markExcluded);

        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant expiresAt = Instant.now().plus(VALIDITY);
        inviteRepository.persist(Invite.create(
                hash(token), email, kind, club, currentUserService.requireCurrentUser(), expiresAt));

        String link = urlTemplate.replace("{token}", token);
        mailer.send(Mail.withText(email, subject(kind, club), body(kind, club, link)));
        return new InviteResponse(email, kind, expiresAt, link);
    }

    private void linkAdmin(Club club, User user) {
        clubAdminRepository.findLink(club.getId(), user.getId()).ifPresentOrElse(
                ClubAdmin::reactivate,
                () -> clubAdminRepository.persist(ClubAdmin.create(club, user)));
    }

    private void linkCoach(Club club, User user) {
        Coach coach = coachRepository.findByUserId(user.getId()).orElseGet(() -> {
            Coach created = Coach.create(user);
            coachRepository.persist(created);
            return created;
        });
        // Reconvite depois de desvincular: reativa o vínculo antigo, a restrição única impede um segundo.
        clubCoachRepository.findByClubAndCoach(club.getId(), coach.getId()).ifPresentOrElse(
                ClubCoach::reactivate,
                () -> clubCoachRepository.persist(ClubCoach.create(club, coach)));
    }

    private static Invite orNotFound(Optional<Invite> invite) {
        return invite.orElseThrow(() -> new NotFoundException("Convite não encontrado"));
    }

    private static Invite requireUsable(Invite invite) {
        if (invite.getAcceptedAt() != null) {
            throw new InviteUnavailableException("Este convite já foi aceito");
        }
        if (invite.getExpiresAt().isBefore(Instant.now())) {
            throw new InviteUnavailableException("Convite expirado. Peça um novo ao clube");
        }
        return invite;
    }

    private static String subject(InviteKind kind, Club club) {
        return switch (kind) {
            case CLUB_ADMIN -> "Convite para administrar o " + club.getName() + " no Bandeja Clube";
            case COACH -> "Convite para dar aulas no " + club.getName();
        };
    }

    private static String body(InviteKind kind, Club club, String link) {
        String role = kind == InviteKind.CLUB_ADMIN ? "administrador do" : "professor no";
        return "Você foi convidado para ser " + role + " " + club.getName() + ".\n\n"
                + "Aceite o convite em: " + link + "\n\n"
                + "O convite vale por 7 dias.";
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
