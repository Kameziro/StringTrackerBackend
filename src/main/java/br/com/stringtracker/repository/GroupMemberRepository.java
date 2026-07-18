package br.com.stringtracker.repository;

import br.com.stringtracker.model.GroupMember;
import br.com.stringtracker.model.PlayerGroup;
import br.com.stringtracker.model.User;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class GroupMemberRepository implements PanacheRepository<GroupMember> {

    public Optional<GroupMember> findByGroupAndUser(PlayerGroup group, User user) {
        return find("group = ?1 and user = ?2", group, user).firstResultOptional();
    }

    public Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId) {
        return find("group.id = ?1 and user.id = ?2", groupId, userId).firstResultOptional();
    }

    public boolean isMember(PlayerGroup group, User user) {
        return count("group = ?1 and user = ?2", group, user) > 0;
    }

    public List<GroupMember> findByGroup(PlayerGroup group) {
        return list("group", group);
    }

    public List<GroupMember> findByUser(User user) {
        return list("user", user);
    }

    public long countByGroup(PlayerGroup group) {
        return count("group", group);
    }

    public List<Long> findMemberUserIds(PlayerGroup group) {
        return findByGroup(group).stream()
                .map(m -> m.getUser().getId())
                .toList();
    }
}
