package br.com.stringtracker.dto;

import br.com.stringtracker.model.ClubPhoto;
import br.com.stringtracker.service.MinioObjectStorage;

public record ClubPhotoResponse(long id, String url, int position) {

    public static ClubPhotoResponse from(ClubPhoto photo, MinioObjectStorage storage) {
        return new ClubPhotoResponse(photo.getId(), storage.toClientMediaUrl(photo.getUrl()), photo.getPosition());
    }
}
