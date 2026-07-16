package br.com.stringtracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "game_interests", uniqueConstraints = {
        @UniqueConstraint(name = "uk_game_interests_game_user", columnNames = {"game_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class GameInterest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private OpenGame game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GameInterestStatus status;

    public static GameInterest create(OpenGame game, User user) {
        GameInterest interest = new GameInterest();
        interest.game = game;
        interest.user = user;
        interest.status = GameInterestStatus.INTERESTED;
        return interest;
    }
}
