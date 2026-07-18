package br.com.stringtracker.dto;

import br.com.stringtracker.model.PlayerGroup;
import br.com.stringtracker.service.MinioObjectStorage;
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
    private String avatarUrl;
    private String bannerUrl;

    public static GroupResponse from(
            PlayerGroup group,
            long memberCount,
            boolean joined,
            MinioObjectStorage storage
    ) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                memberCount,
                joined,
                storage != null ? storage.toClientMediaUrl(group.getAvatarUrl()) : group.getAvatarUrl(),
                storage != null ? storage.toClientMediaUrl(group.getBannerUrl()) : group.getBannerUrl()
        );
    }
}
