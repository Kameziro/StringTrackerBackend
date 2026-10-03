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
@Table(name = "club_admins", uniqueConstraints = {
        @UniqueConstraint(name = "uk_club_admins_club_user", columnNames = {"club_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class ClubAdmin extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public static ClubAdmin create(Club club, User user) {
        ClubAdmin admin = new ClubAdmin();
        admin.club = club;
        admin.user = user;
        return admin;
    }
}
