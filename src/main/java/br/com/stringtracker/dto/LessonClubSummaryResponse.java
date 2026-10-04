package br.com.stringtracker.dto;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubPaymentStatus;
import br.com.stringtracker.service.MinioObjectStorage;

import java.time.Instant;

/** Clube da cidade na lista de aulas do aluno; {@code nextFreeAt} é o início do primeiro horário livre. */
public record LessonClubSummaryResponse(
        long id,
        String name,
        String address,
        String logoUrl,
        boolean acceptsPix,
        Instant nextFreeAt
) {

    public static LessonClubSummaryResponse from(Club club, Instant nextFreeAt, MinioObjectStorage storage) {
        return new LessonClubSummaryResponse(club.getId(), club.getName(), club.getAddress(),
                storage.toClientMediaUrl(club.getLogoUrl()), club.getPaymentStatus() == ClubPaymentStatus.CONNECTED,
                nextFreeAt);
    }
}
