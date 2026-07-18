package br.com.stringtracker.service;

import br.com.stringtracker.dto.CreateGroupRequest;
import br.com.stringtracker.dto.GroupDetailResponse;
import br.com.stringtracker.dto.GroupResponse;
import br.com.stringtracker.dto.UpdateGroupMemberRoleRequest;
import br.com.stringtracker.model.GroupMember;
import br.com.stringtracker.model.GroupRole;
import br.com.stringtracker.model.PlayerGroup;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.GroupMemberRepository;
import br.com.stringtracker.repository.PlayerGroupRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class GroupService {

    @Inject
    PlayerGroupRepository playerGroupRepository;

    @Inject
    GroupMemberRepository groupMemberRepository;

    @Inject
    MinioObjectStorage minioObjectStorage;

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
        GroupMember me = groupMemberRepository.findByGroupAndUser(group, user).orElse(null);
        List<GroupMember> members = groupMemberRepository.findByGroup(group).stream()
                .sorted(roleThenName())
                .toList();
        return GroupDetailResponse.from(
                group,
                me != null,
                me != null ? me.getRole() : null,
                members,
                minioObjectStorage
        );
    }

    @Transactional
    public GroupResponse create(User user, CreateGroupRequest request) {
        String name = request.getName().trim();
        if (playerGroupRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new BadRequestException("Já existe um grupo com esse nome");
        }
        PlayerGroup group = PlayerGroup.create(name, user);
        playerGroupRepository.persist(group);
        groupMemberRepository.persist(GroupMember.create(group, user, GroupRole.ADMIN));
        return toResponse(group, user);
    }

    @Transactional
    public GroupResponse join(User user, Long groupId) {
        PlayerGroup group = requireGroup(groupId);
        if (!groupMemberRepository.isMember(group, user)) {
            groupMemberRepository.persist(GroupMember.create(group, user, GroupRole.MEMBER));
        }
        return toResponse(group, user);
    }

    @Transactional
    public GroupResponse leave(User user, Long groupId) {
        PlayerGroup group = requireGroup(groupId);
        GroupMember me = groupMemberRepository.findByGroupAndUser(group, user)
                .orElseThrow(() -> new BadRequestException("Você não está neste grupo"));
        if (me.getRole() == GroupRole.ADMIN && countAdmins(group) <= 1) {
            throw new BadRequestException(
                    "Você é o único administrador. Escolha outra pessoa como administrador antes de sair.");
        }
        groupMemberRepository.delete(me);
        return toResponse(group, user);
    }

    @Transactional
    public GroupDetailResponse updateMemberRole(
            User actor,
            Long groupId,
            Long targetUserId,
            UpdateGroupMemberRoleRequest request
    ) {
        PlayerGroup group = requireGroup(groupId);
        GroupMember actorMember = requireMembership(group, actor);
        if (actorMember.getRole() != GroupRole.ADMIN) {
            throw new ForbiddenException("Só administradores podem mudar a função de alguém");
        }
        if (request.getRole() == null) {
            throw new BadRequestException("Escolha uma função");
        }

        GroupMember target = groupMemberRepository.findByGroupIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new NotFoundException("Membro não encontrado"));

        if (request.getRole() != GroupRole.ADMIN
                && target.getRole() == GroupRole.ADMIN
                && countAdmins(group) <= 1) {
            throw new BadRequestException(
                    "Não dá para tirar a função de administrador: o grupo ficaria sem responsável. "
                            + "Escolha outra pessoa como administrador primeiro.");
        }

        target.setRole(request.getRole());
        return getDetail(actor, groupId);
    }

    @Transactional
    public GroupDetailResponse uploadImage(
            User user,
            Long groupId,
            MinioObjectStorage.GroupImageKind kind,
            FileUpload file
    ) {
        if (file == null || file.size() <= 0) {
            throw new BadRequestException("Envie um arquivo de imagem no campo file");
        }
        PlayerGroup group = requireGroup(groupId);
        GroupMember me = requireMembership(group, user);
        if (me.getRole() != GroupRole.ADMIN && me.getRole() != GroupRole.MODERATOR) {
            throw new ForbiddenException(
                    "Só administradores ou moderadores podem alterar a imagem do grupo");
        }

        String previousUrl = kind == MinioObjectStorage.GroupImageKind.AVATAR
                ? group.getAvatarUrl()
                : group.getBannerUrl();
        String previousKey = minioObjectStorage.extractObjectKey(previousUrl);

        try (InputStream in = Files.newInputStream(file.uploadedFile())) {
            String url = minioObjectStorage.uploadGroupImage(
                    group.getId(),
                    kind,
                    in,
                    file.size(),
                    file.contentType()
            );
            if (kind == MinioObjectStorage.GroupImageKind.AVATAR) {
                group.setAvatarUrl(url);
            } else {
                group.setBannerUrl(url);
            }
        } catch (IOException e) {
            throw new BadRequestException("Não foi possível ler a imagem enviada");
        }

        String newKey = minioObjectStorage.extractObjectKey(
                kind == MinioObjectStorage.GroupImageKind.AVATAR
                        ? group.getAvatarUrl()
                        : group.getBannerUrl()
        );
        if (previousKey != null && newKey != null && !previousKey.equals(newKey)) {
            minioObjectStorage.deleteObjectIfPresent(previousUrl);
        }

        return getDetail(user, groupId);
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

    private GroupMember requireMembership(PlayerGroup group, User user) {
        return groupMemberRepository.findByGroupAndUser(group, user)
                .orElseThrow(() -> new ForbiddenException("Você não é membro deste grupo"));
    }

    private long countAdmins(PlayerGroup group) {
        return groupMemberRepository.findByGroup(group).stream()
                .filter(m -> m.getRole() == GroupRole.ADMIN)
                .count();
    }

    private static Comparator<GroupMember> roleThenName() {
        return Comparator
                .comparingInt((GroupMember m) -> roleRank(m.getRole()))
                .thenComparing(m -> m.getUser().getName(), String.CASE_INSENSITIVE_ORDER);
    }

    private static int roleRank(GroupRole role) {
        if (role == null) {
            return 3;
        }
        return switch (role) {
            case ADMIN -> 0;
            case MODERATOR -> 1;
            case MEMBER -> 2;
        };
    }

    private GroupResponse toResponse(PlayerGroup group, User user) {
        return GroupResponse.from(
                group,
                groupMemberRepository.countByGroup(group),
                groupMemberRepository.isMember(group, user),
                minioObjectStorage
        );
    }
}
