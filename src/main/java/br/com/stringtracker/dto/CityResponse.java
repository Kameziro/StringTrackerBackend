package br.com.stringtracker.dto;

import br.com.stringtracker.model.City;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CityResponse {

    private Long id;
    private String name;
    private Long stateId;

    public static CityResponse from(City city) {
        return new CityResponse(city.getId(), city.getName(), city.getState().getId());
    }
}
