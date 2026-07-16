package br.com.stringtracker.service;

import br.com.stringtracker.dto.CreateGroupRequest;
import br.com.stringtracker.dto.GroupDetailResponse;
import br.com.stringtracker.dto.GroupResponse;
import br.com.stringtracker.model.GroupMember;
import br.com.stringtracker.model.PlayerGroup;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.GroupMemberRepository;
import br.com.stringtracker.repository.PlayerGroupRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class GroupService {

    @Inject
    PlayerGroupRepository playerGroupRepository;

    @Inject
    GroupMemberRepository groupMemberRepository;

    public List<GroupResponse> listAll(User user) {
        return playerGroupRepository.listAllOrdered().stream()
                .map(g -> toResponse(g, user))
                .toList();
    }

    public List<GroupResponse> listMine(User user) {
        return groupMemberRepository.findByUser(user).stream()
                .map(GroupMember::getGroup)
                .map(g -> toResponse(g, user))
                .toList();
    }

    public GroupDetailResponse getDetail(User user, Long groupId) {
        PlayerGroup group = requireGroup(groupId);
        List<GroupMember> members = groupMemberRepository.findByGroup(group).stream()
                .sorted(Comparator.comparing(m -> m.getUser().getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        return GroupDetailResponse.from(group, groupMemberRepository.isMember(group, user), members);
    }

    @Transactional
    public GroupResponse create(User user, CreateGroupRequest request) {
        String name = request.name().trim();
        if (playerGroupRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new BadRequestException("Já existe um grupo com esse nome");
        }
        PlayerGroup group = PlayerGroup.create(name, user);
        playerGroupRepository.persist(group);
        groupMemberRepository.persist(GroupMember.create(group, user));
        return toResponse(group, user);
    }

    @Transactional
    public GroupResponse join(User user, Long groupId) {
        PlayerGroup group = requireGroup(groupId);
        if (!groupMemberRepository.isMember(group, user)) {
            groupMemberRepository.persist(GroupMember.create(group, user));
        }
        return toResponse(group, user);
    }

    @Transactional
    public GroupResponse leave(User user, Long groupId) {
        PlayerGroup group = requireGroup(groupId);
        groupMemberRepository.delete("group = ?1 and user = ?2", group, user);
        return toResponse(group, user);
    }

    public PlayerGroup requireGroup(Long id) {
        return playerGroupRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Grupo não encontrado"));
    }

    public boolean isMember(PlayerGroup group, User user) {
        return groupMemberRepository.isMember(group, user);
    }

    public List<Long> memberUserIds(PlayerGroup group) {
        return groupMemberRepository.findMemberUserIds(group);
    }

    private GroupResponse toResponse(PlayerGroup group, User user) {
        return GroupResponse.from(
                group,
                groupMemberRepository.countByGroup(group),
                groupMemberRepository.isMember(group, user)
        );
    }
}
