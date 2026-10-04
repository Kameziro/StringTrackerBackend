package br.com.stringtracker.dto;

import br.com.stringtracker.model.Coach;

public record CoachOffersResponse(boolean singles, boolean doubles, boolean group) {

    public static CoachOffersResponse from(Coach coach) {
        return new CoachOffersResponse(coach.isOffersSingles(), coach.isOffersDoubles(), coach.isOffersGroup());
    }
}
