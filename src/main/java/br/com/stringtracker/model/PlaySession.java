package br.com.stringtracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "play_sessions")
@Getter
@Setter
@NoArgsConstructor
public class PlaySession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "racket_id", nullable = false)
    private Racket racket;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "date_played", nullable = false)
    private LocalDate datePlayed;

    public static PlaySession create(Racket racket, int durationMinutes, LocalDate datePlayed) {
        PlaySession session = new PlaySession();
        session.racket = racket;
        session.durationMinutes = durationMinutes;
        session.datePlayed = datePlayed;
        return session;
    }
}
