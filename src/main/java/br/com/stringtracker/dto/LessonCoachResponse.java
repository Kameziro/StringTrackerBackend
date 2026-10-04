package br.com.stringtracker.dto;

import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.ClubPaymentStatus;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.service.MinioObjectStorage;

import java.util.List;

/**
 * Página do professor para o aluno: o perfil único dele e, para cada clube onde atende, os tipos de aula com o preço
 * do clube e todos os horários livres que ainda aceitam reserva.
 */
public record LessonCoachResponse(
        long coachId,
        String name,
        String avatarUrl,
        String bio,
        List<ClubEntry> clubs
) {

    public record ClubEntry(
            long clubId,
            String name,
            String address,
            String logoUrl,
            boolean acceptsPix,
            List<LessonTypeOption> types,
            List<LessonSlotOption> slots
    ) {

        public static ClubEntry from(ClubCoach link, List<LessonSlotOption> slots, MinioObjectStorage storage) {
            var club = link.getClub();
            return new ClubEntry(club.getId(), club.getName(), club.getAddress(),
                    storage.toClientMediaUrl(club.getLogoUrl()), club.getPaymentStatus() == ClubPaymentStatus.CONNECTED,
                    LessonTypeOption.offeredBy(link), slots);
        }
    }

    public static LessonCoachResponse from(Coach coach, List<ClubEntry> clubs, MinioObjectStorage storage) {
        return new LessonCoachResponse(coach.getId(), coach.getUser().getName(),
                storage.toClientMediaUrl(coach.getUser().getAvatarUrl()), coach.getBio(), clubs);
    }
}
