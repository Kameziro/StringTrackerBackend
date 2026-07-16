package br.com.stringtracker.dto;

import br.com.stringtracker.model.PlayerGroup;

public record GroupResponse(
        Long id,
        String name,
        long memberCount,
        boolean joined
) {
    public static GroupResponse from(PlayerGroup group, long memberCount, boolean joined) {
        return new GroupResponse(group.getId(), group.getName(), memberCount, joined);
    }
}
