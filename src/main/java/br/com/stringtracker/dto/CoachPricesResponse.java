package br.com.stringtracker.dto;

import br.com.stringtracker.model.ClubCoach;

public record CoachPricesResponse(long clubId, long coachId, Long singlesCents, Long doublesCents, Long groupCents) {

    public static CoachPricesResponse from(ClubCoach link) {
        return new CoachPricesResponse(
                link.getClub().getId(),
                link.getCoach().getId(),
                link.getPriceSinglesCents(),
                link.getPriceDoublesCents(),
                link.getPriceGroupCents());
    }
}
