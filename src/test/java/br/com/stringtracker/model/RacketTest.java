package br.com.stringtracker.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RacketTest {

    @Test
    void create_startsWithZeroHours() {
        User owner = new User();
        Racket racket = Racket.create(owner, "Babolat", "Pure Drive", "Luxilon", 52.0, LocalDate.of(2026, 6, 1));
        assertEquals(0.0, racket.getTotalHoursPlayed());
    }

    @Test
    void addPlayMinutes_accumulatesHoursAsMinutesOverSixty() {
        User owner = new User();
        Racket racket = Racket.create(owner, "Babolat", "Pure Drive", "Luxilon", 52.0, LocalDate.of(2026, 6, 1));
        racket.addPlayMinutes(90);
        assertEquals(1.5, racket.getTotalHoursPlayed());
    }
}
