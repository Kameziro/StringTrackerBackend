package br.com.stringtracker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record CreatePlaySessionRequest(
        @NotNull Long racketId,
        @NotNull @Positive Integer durationMinutes,
        @NotNull LocalDate datePlayed
) {
}
