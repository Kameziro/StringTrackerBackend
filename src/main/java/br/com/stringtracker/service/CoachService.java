package br.com.stringtracker.service;

import br.com.stringtracker.dto.CoachOffersResponse;
import br.com.stringtracker.dto.CoachPricesResponse;
import br.com.stringtracker.dto.UpdateCoachOffersRequest;
import br.com.stringtracker.dto.UpdateCoachPricesRequest;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.repository.ClubCoachRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

/** Tipos de aula oferecidos (pelo professor) e preços por clube (pelo admin do clube). */
@ApplicationScoped
public class CoachService {

    @Inject
    ClubAccessService access;

    @Inject
    ClubCoachRepository clubCoachRepository;

    @Transactional
    public CoachOffersResponse updateOffers(UpdateCoachOffersRequest request) {
        Coach coach = access.requireCurrentCoach();
        coach.setOffersSingles(request.singles());
        coach.setOffersDoubles(request.doubles());
        coach.setOffersGroup(request.group());
        return CoachOffersResponse.from(coach);
    }

    @Transactional
    public CoachPricesResponse updatePrices(long clubId, long coachId, UpdateCoachPricesRequest request) {
        access.requireClubAdmin(clubId);
        ClubCoach link = clubCoachRepository.findByClubAndCoach(clubId, coachId)
                .filter(ClubCoach::isActive)
                .orElseThrow(() -> new NotFoundException("Professor não vinculado a este clube"));
        link.setPriceSinglesCents(request.singlesCents());
        link.setPriceDoublesCents(request.doublesCents());
        link.setPriceGroupCents(request.groupCents());
        return CoachPricesResponse.from(link);
    }
}
