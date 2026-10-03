package br.com.stringtracker.dto;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubPaymentStatus;

/** Clube na lista da plataforma; {@code cityId} e {@code cityName} são nulos nos clubes antigos. */
public record PlatformClubResponse(
        long id,
        String name,
        Long cityId,
        String cityName,
        ClubPaymentStatus paymentStatus
) {

    public static PlatformClubResponse from(Club club) {
        var city = club.getCity();
        return new PlatformClubResponse(
                club.getId(),
                club.getName(),
                city == null ? null : city.getId(),
                city == null ? null : city.getName(),
                club.getPaymentStatus());
    }
}
