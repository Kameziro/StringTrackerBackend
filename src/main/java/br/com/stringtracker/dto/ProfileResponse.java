package br.com.stringtracker.dto;

import br.com.stringtracker.model.City;
import br.com.stringtracker.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {

    private Long id;
    private String name;
    private String email;
    private Integer category;
    private Long cityId;
    private String cityName;
    private Long stateId;
    private String stateUf;
    private boolean availableToday;
    private Instant availableTodayAt;

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
