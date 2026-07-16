package br.com.stringtracker.dto;

import br.com.stringtracker.model.User;

public record PlayerResponse(
        Long id,
        String name,
        Integer category,
        boolean availableToday
) {
    public static PlayerResponse from(User user) {
        return new PlayerResponse(
                user.getId(),
                user.getName(),
                user.getCategory(),
                user.isAvailableToday()
        );
    }
}
