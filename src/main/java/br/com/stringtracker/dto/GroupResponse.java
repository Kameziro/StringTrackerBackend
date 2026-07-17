package br.com.stringtracker.dto;

import br.com.stringtracker.model.PlayerGroup;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupResponse {

    private Long id;
    private String name;
    private long memberCount;
    private boolean joined;

    public static GroupResponse from(PlayerGroup group, long memberCount, boolean joined) {
        return new GroupResponse(group.getId(), group.getName(), memberCount, joined);
    }
}
