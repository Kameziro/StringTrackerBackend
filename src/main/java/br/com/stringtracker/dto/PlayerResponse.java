package br.com.stringtracker.dto;

import br.com.stringtracker.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerResponse {

    private Long id;
    private String name;
    private Integer category;
    private boolean availableToday;

    public static PlayerResponse from(User user) {
        return new PlayerResponse(
                user.getId(),
                user.getName(),
                user.getCategory(),
                user.isAvailableToday()
        );
    }
}
