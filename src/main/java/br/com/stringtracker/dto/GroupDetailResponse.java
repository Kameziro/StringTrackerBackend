package br.com.stringtracker.dto;

import br.com.stringtracker.model.GroupMember;
import br.com.stringtracker.model.PlayerGroup;
import br.com.stringtracker.model.User;
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
    private Long createdById;
    private String createdByName;
    private List<MemberResponse> members;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberResponse {

        private Long userId;
        private String name;
        private Integer category;
        private boolean availableToday;

        public static MemberResponse from(User user) {
            return new MemberResponse(
                    user.getId(),
                    user.getName(),
                    user.getCategory(),
                    user.isAvailableToday()
            );
        }
    }

    public static GroupDetailResponse from(
            PlayerGroup group,
            boolean joined,
            List<GroupMember> members
    ) {
        List<MemberResponse> memberResponses = members.stream()
                .map(m -> MemberResponse.from(m.getUser()))
                .toList();
        return new GroupDetailResponse(
                group.getId(),
                group.getName(),
                memberResponses.size(),
                joined,
                group.getCreatedBy().getId(),
                group.getCreatedBy().getName(),
                memberResponses
        );
    }
}
