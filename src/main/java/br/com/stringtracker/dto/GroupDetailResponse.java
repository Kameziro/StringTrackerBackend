package br.com.stringtracker.dto;

import br.com.stringtracker.model.GroupMember;
import br.com.stringtracker.model.GroupRole;
import br.com.stringtracker.model.PlayerGroup;
import br.com.stringtracker.model.User;
import br.com.stringtracker.service.MinioObjectStorage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupDetailResponse {

    private Long id;
    private String name;
    private long memberCount;
    private boolean joined;
    private GroupRole myRole;
    private Long createdById;
    private String createdByName;
    private String avatarUrl;
    private String bannerUrl;
    private List<MemberResponse> members;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberResponse {

        private Long userId;
        private String name;
        private Integer category;
        private boolean availableToday;
        private GroupRole role;

        public static MemberResponse from(GroupMember member) {
            User user = member.getUser();
            return new MemberResponse(
                    user.getId(),
                    user.getName(),
                    user.getCategory(),
                    user.isAvailableToday(),
                    member.getRole() != null ? member.getRole() : GroupRole.MEMBER
            );
        }
    }

    public static GroupDetailResponse from(
            PlayerGroup group,
            boolean joined,
            GroupRole myRole,
            List<GroupMember> members,
            MinioObjectStorage storage
    ) {
        List<MemberResponse> memberResponses = members.stream()
                .map(MemberResponse::from)
                .toList();
        return new GroupDetailResponse(
                group.getId(),
                group.getName(),
                memberResponses.size(),
                joined,
                myRole,
                group.getCreatedBy().getId(),
                group.getCreatedBy().getName(),
                storage != null ? storage.toClientMediaUrl(group.getAvatarUrl()) : group.getAvatarUrl(),
                storage != null ? storage.toClientMediaUrl(group.getBannerUrl()) : group.getBannerUrl(),
                memberResponses
        );
    }
}
