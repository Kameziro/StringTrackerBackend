package br.com.stringtracker.dto;

import br.com.stringtracker.model.Club;

public record ClubResponse(Long id, String name) {
    public static ClubResponse from(Club club) {
        return new ClubResponse(club.getId(), club.getName());
    }
}
