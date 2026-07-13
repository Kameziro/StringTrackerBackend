package br.com.stringtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record CreateRacketRequest(
        @NotBlank String brand,
        @NotBlank String model,
        @NotBlank String stringModel,
        @NotNull @Positive Double tensionLbs,
        @NotNull LocalDate dateStrung
) {
}
