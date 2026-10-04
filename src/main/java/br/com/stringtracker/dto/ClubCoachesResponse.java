package br.com.stringtracker.dto;

import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.service.MinioObjectStorage;

import java.util.List;

/** Professores vinculados ao clube, com os tipos que oferecem e os preços do clube, e os convites de professor ainda não aceitos. */
public record ClubCoachesResponse(List<Coach> coaches, List<PendingInviteResponse> pendingInvites) {

    /** Preço nulo: o clube ainda não definiu. Um tipo pode ter preço sem ser oferecido, e o contrário também. */
    public record Coach(
            long coachId,
            String name,
            String email,
            String avatarUrl,
            String bio,
            CoachOffersResponse offers,
            CoachPricesResponse prices
    ) {

        public static Coach from(ClubCoach link, MinioObjectStorage storage) {
            var coach = link.getCoach();
            return new Coach(coach.getId(), coach.getUser().getName(), coach.getUser().getEmail(),
                    storage.toClientMediaUrl(coach.getUser().getAvatarUrl()), coach.getBio(),
                    CoachOffersResponse.from(coach), CoachPricesResponse.from(link));
        }
    }
}
