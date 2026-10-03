package br.com.stringtracker.service;

import br.com.stringtracker.repository.ClubAdminRepository;
import br.com.stringtracker.repository.CoachRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

import java.util.Set;

/**
 * Autorização por papel da agenda de professores. Os papéis vivem no banco
 * ({@code users.platform_admin}, {@code club_admins}, {@code coaches}), não no Keycloak.
 * Cada método responde 403 ({@link ForbiddenException}) quando o usuário atual não tem o papel.
 */
@ApplicationScoped
public class ClubAccessService {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ClubAdminRepository clubAdminRepository;

    @Inject
    CoachRepository coachRepository;

    public void requirePlatformAdmin() {
        if (!currentUserService.requireCurrentUser().isPlatformAdmin()) {
            throw new ForbiddenException("Acesso restrito aos administradores da plataforma");
        }
    }

    public void requireClubAdmin(long clubId) {
        long userId = currentUserService.requireCurrentUser().getId();
        if (!clubAdminRepository.isAdmin(clubId, userId)) {
            throw new ForbiddenException("Você não é administrador deste clube");
        }
    }

    public void requireCoach(long coachId) {
        long userId = currentUserService.requireCurrentUser().getId();
        if (!coachRepository.existsByIdAndUserId(coachId, userId)) {
            throw new ForbiddenException("Você não é este professor");
        }
    }

    public Set<Long> adminClubIds() {
        return clubAdminRepository.findClubIdsByUserId(currentUserService.requireCurrentUser().getId());
    }
}
