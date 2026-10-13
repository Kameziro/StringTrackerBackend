package br.com.stringtracker.service;

import br.com.stringtracker.dto.MyAccessResponse;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.ClubAdminRepository;
import br.com.stringtracker.repository.ClubCoachRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Papéis do usuário atual na agenda de professores; só vínculos e clubes ativos contam. */
@ApplicationScoped
public class MyAccessService {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ClubAdminRepository clubAdminRepository;

    @Inject
    ClubCoachRepository clubCoachRepository;

    @Inject
    MinioObjectStorage storage;

    @Transactional
    public MyAccessResponse get() {
        User user = currentUserService.requireCurrentUser();
        return new MyAccessResponse(
                user.isPlatformAdmin(),
                clubAdminRepository.listActiveClubsOfUser(user.getId()).stream()
                        .map(club -> MyAccessResponse.AdminClub.from(club, storage)).toList(),
                clubCoachRepository.existsActiveOfUser(user.getId()));
    }
}
