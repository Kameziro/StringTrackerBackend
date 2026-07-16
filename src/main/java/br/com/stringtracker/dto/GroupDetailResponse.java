package br.com.stringtracker.dto;

import br.com.stringtracker.model.GroupMember;
import br.com.stringtracker.model.PlayerGroup;
import br.com.stringtracker.model.User;

import java.util.List;

public record GroupDetailResponse(
        Long id,
        String name,
        long memberCount,
        boolean joined,
        Long createdById,
        String createdByName,
        List<MemberResponse> members
) {
    public record MemberResponse(
            Long userId,
            String name,
            Integer category,
            boolean availableToday
    ) {
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
