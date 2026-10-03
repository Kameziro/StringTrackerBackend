package br.com.stringtracker.service;

import br.com.stringtracker.dto.CreateClubRequest;
import br.com.stringtracker.dto.InviteResponse;
import br.com.stringtracker.dto.PlatformClubResponse;
import br.com.stringtracker.model.City;
import br.com.stringtracker.model.Club;
import br.com.stringtracker.repository.CityRepository;
import br.com.stringtracker.repository.ClubRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;

import java.util.List;

/** Área da plataforma: cadastra clubes e convida o primeiro admin. Só para admin da plataforma. */
@ApplicationScoped
public class PlatformService {

    @Inject
    ClubAccessService access;

    @Inject
    ClubRepository clubRepository;

    @Inject
    CityRepository cityRepository;

    @Inject
    InviteService inviteService;

    @Transactional
    public List<PlatformClubResponse> listClubs() {
        access.requirePlatformAdmin();
        return clubRepository.listAllActive().stream().map(PlatformClubResponse::from).toList();
    }

    @Transactional
    public PlatformClubResponse createClub(CreateClubRequest request) {
        access.requirePlatformAdmin();
        String name = request.name().trim();
        City city = cityRepository.findByIdOptional(request.cityId())
                .orElseThrow(() -> new BadRequestException("Cidade não encontrada"));
        if (clubRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new BadRequestException("Já existe um clube com esse nome");
        }
        Club club = Club.create(name);
        club.setCity(city);
        clubRepository.persist(club);
        return PlatformClubResponse.from(club);
    }

    public InviteResponse inviteAdmin(long clubId, String email) {
        access.requirePlatformAdmin();
        return inviteService.inviteClubAdmin(clubId, email);
    }
}
