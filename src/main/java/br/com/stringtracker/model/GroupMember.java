package br.com.stringtracker.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "group_members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_group_members_group_user", columnNames = {"group_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class GroupMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private PlayerGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public static GroupMember create(PlayerGroup group, User user) {
        GroupMember member = new GroupMember();
        member.group = group;
        member.user = user;
        return member;
    }
}
