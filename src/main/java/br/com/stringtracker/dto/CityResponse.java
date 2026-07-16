package br.com.stringtracker.dto;

import br.com.stringtracker.model.City;

public record CityResponse(Long id, String name, Long stateId) {
    public static CityResponse from(City city) {
        return new CityResponse(city.getId(), city.getName(), city.getState().getId());
    }
}
