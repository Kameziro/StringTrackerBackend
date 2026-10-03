package br.com.stringtracker.dto;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubPaymentStatus;
import br.com.stringtracker.model.ClubPhoto;
import br.com.stringtracker.service.MinioObjectStorage;

import java.util.List;

/** Perfil do clube para o admin. Os tokens da conta de recebimento nunca entram aqui. */
public record ClubProfileResponse(
        long id,
        String name,
        Long cityId,
        String cityName,
        String address,
        String whatsapp,
        String logoUrl,
        ClubPaymentStatus paymentStatus,
        List<ClubPhotoResponse> photos
) {

    public static ClubProfileResponse from(Club club, List<ClubPhoto> photos, MinioObjectStorage storage) {
        var city = club.getCity();
        return new ClubProfileResponse(
                club.getId(),
                club.getName(),
                city == null ? null : city.getId(),
                city == null ? null : city.getName(),
                club.getAddress(),
                club.getWhatsapp(),
                storage.toClientMediaUrl(club.getLogoUrl()),
                club.getPaymentStatus(),
                photos.stream().map(photo -> ClubPhotoResponse.from(photo, storage)).toList());
    }
}
