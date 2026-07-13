package br.com.stringtracker.dto;

import br.com.stringtracker.model.Racket;

import java.time.LocalDate;

public record RacketResponse(
        Long id,
        String brand,
        String model,
        String stringModel,
        double tensionLbs,
        LocalDate dateStrung,
        double totalHoursPlayed
) {
    public static RacketResponse from(Racket racket) {
        return new RacketResponse(
                racket.getId(),
                racket.getBrand(),
                racket.getModel(),
                racket.getStringModel(),
                racket.getTensionLbs(),
                racket.getDateStrung(),
                racket.getTotalHoursPlayed()
        );
    }
}
