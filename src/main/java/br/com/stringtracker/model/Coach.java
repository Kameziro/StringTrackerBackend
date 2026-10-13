package br.com.stringtracker.model;

import br.com.stringtracker.model.schedule.LessonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Perfil único do professor; a foto é o {@code User.avatarUrl}. */
@Entity
@Table(name = "coaches", uniqueConstraints = {
        @UniqueConstraint(name = "uk_coaches_user", columnNames = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Coach extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "text")
    private String bio;

    @Column(name = "offers_singles", nullable = false)
    private boolean offersSingles = true;

    @Column(name = "offers_doubles", nullable = false)
    private boolean offersDoubles = true;

    @Column(name = "offers_group", nullable = false)
    private boolean offersGroup;

    public boolean offers(LessonType type) {
        return switch (type) {
            case SINGLES -> offersSingles;
            case DOUBLES -> offersDoubles;
            case GROUP -> offersGroup;
        };
    }

    public static Coach create(User user) {
        Coach coach = new Coach();
        coach.user = user;
        return coach;
    }
}
