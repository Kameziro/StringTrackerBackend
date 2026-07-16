package br.com.stringtracker.dto;

import br.com.stringtracker.model.City;
import br.com.stringtracker.model.User;

import java.time.Instant;

public record ProfileResponse(
        Long id,
        String name,
        String email,
        Integer category,
        Long cityId,
        String cityName,
        Long stateId,
        String stateUf,
        boolean availableToday,
        Instant availableTodayAt
) {
    public static ProfileResponse from(User user) {
        City city = user.getCity();
        return new ProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getCategory(),
                city != null ? city.getId() : null,
                city != null ? city.getName() : null,
                city != null ? city.getState().getId() : null,
                city != null ? city.getState().getUf() : null,
                user.isAvailableToday(),
                user.getAvailableTodayAt()
        );
    }
}
