package br.com.stringtracker.dto;

import br.com.stringtracker.model.PlaySession;
import br.com.stringtracker.model.Racket;

import java.time.LocalDate;

public record PlaySessionResponse(
        Long id,
        Long racketId,
        int durationMinutes,
        LocalDate datePlayed,
        double racketTotalHoursPlayed
) {
    public static PlaySessionResponse from(PlaySession session, Racket racket) {
        return new PlaySessionResponse(
                session.getId(),
                racket.getId(),
                session.getDurationMinutes(),
                session.getDatePlayed(),
                racket.getTotalHoursPlayed()
        );
    }
}
