package br.com.stringtracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "open_games")
@Getter
@Setter
@NoArgsConstructor
public class OpenGame extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private User organizer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private PlayerGroup group;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private int category;

    @Column(nullable = false)
    private int capacity = 4;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OpenGameStatus status;

    public static OpenGame create(
            User organizer,
            Club club,
            Instant startsAt,
            Instant endsAt,
            int category,
            int capacity,
            PlayerGroup group
    ) {
        OpenGame game = new OpenGame();
        game.organizer = organizer;
        game.club = club;
        game.startsAt = startsAt;
        game.endsAt = endsAt;
        game.category = category;
        game.capacity = capacity;
        game.group = group;
        game.status = OpenGameStatus.OPEN;
        return game;
    }

    public boolean isGroupScoped() {
        return group != null;
    }

    public int seatsNeeded() {
        return Math.max(0, capacity - 1);
    }
}
