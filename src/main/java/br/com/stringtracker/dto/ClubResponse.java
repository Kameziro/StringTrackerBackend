package br.com.stringtracker.dto;

import br.com.stringtracker.model.Club;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClubResponse {

    private Long id;
    private String name;

    public static ClubResponse from(Club club) {
        return new ClubResponse(club.getId(), club.getName());
    }
}
