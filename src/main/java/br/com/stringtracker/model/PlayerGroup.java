package br.com.stringtracker.model;

import jakarta.persistence.Column;
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
@Table(name = "player_groups", uniqueConstraints = {
        @UniqueConstraint(name = "uk_player_groups_name", columnNames = "name")
})
@Getter
@Setter
@NoArgsConstructor
public class PlayerGroup extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "banner_url", length = 512)
    private String bannerUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    public static PlayerGroup create(String name, User createdBy) {
        PlayerGroup group = new PlayerGroup();
        group.name = name;
        group.createdBy = createdBy;
        return group;
    }
}
