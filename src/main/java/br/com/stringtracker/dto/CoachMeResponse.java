package br.com.stringtracker.dto;

import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.service.MinioObjectStorage;

import java.util.List;

/**
 * O professor logado: os tipos de aula que ele oferece ({@code PUT /api/coach/me/offers}) e, por clube ativo onde
 * atende, os preços que o clube definiu (preço nulo: o clube ainda não definiu).
 */
public record CoachMeResponse(
        long coachId,
        String name,
        String avatarUrl,
        String bio,
        CoachOffersResponse offers,
        List<Club> clubs
) {

    public record Club(long clubId, String name, String logoUrl, CoachPricesResponse prices) {

        static Club from(ClubCoach link, MinioObjectStorage storage) {
            var club = link.getClub();
            return new Club(club.getId(), club.getName(), storage.toClientMediaUrl(club.getLogoUrl()),
                    CoachPricesResponse.from(link));
        }
    }

    public static CoachMeResponse from(Coach coach, List<ClubCoach> links, MinioObjectStorage storage) {
        var user = coach.getUser();
        return new CoachMeResponse(coach.getId(), user.getName(), storage.toClientMediaUrl(user.getAvatarUrl()),
                coach.getBio(), CoachOffersResponse.from(coach),
                links.stream().map(link -> Club.from(link, storage)).toList());
    }
}
