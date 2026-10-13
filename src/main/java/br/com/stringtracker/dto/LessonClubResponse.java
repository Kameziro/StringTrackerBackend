package br.com.stringtracker.dto;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.ClubPaymentStatus;
import br.com.stringtracker.model.ClubPhoto;
import br.com.stringtracker.service.MinioObjectStorage;

import java.util.List;

/**
 * Página do clube para o aluno: identidade, fotos, se aceita Pix e os professores com os tipos de aula que reservam
 * e os próximos horários livres. Sem {@code acceptsPix} o app mostra os horários sem a opção de reservar.
 */
public record LessonClubResponse(
        long id,
        String name,
        String address,
        String whatsapp,
        String logoUrl,
        List<ClubPhotoResponse> photos,
        boolean acceptsPix,
        List<CoachEntry> coaches
) {

    public record CoachEntry(
            long coachId,
            String name,
            String avatarUrl,
            String bio,
            List<LessonTypeOption> types,
            List<LessonSlotOption> nextSlots
    ) {

        public static CoachEntry from(ClubCoach link, List<LessonSlotOption> nextSlots, MinioObjectStorage storage) {
            var coach = link.getCoach();
            return new CoachEntry(coach.getId(), coach.getUser().getName(),
                    storage.toClientMediaUrl(coach.getUser().getAvatarUrl()), coach.getBio(),
                    LessonTypeOption.offeredBy(link), nextSlots);
        }
    }

    public static LessonClubResponse from(Club club, List<ClubPhoto> photos, List<CoachEntry> coaches,
                                          MinioObjectStorage storage) {
        return new LessonClubResponse(club.getId(), club.getName(), club.getAddress(), club.getWhatsapp(),
                storage.toClientMediaUrl(club.getLogoUrl()),
                photos.stream().map(photo -> ClubPhotoResponse.from(photo, storage)).toList(),
                club.getPaymentStatus() == ClubPaymentStatus.CONNECTED, coaches);
    }
}
