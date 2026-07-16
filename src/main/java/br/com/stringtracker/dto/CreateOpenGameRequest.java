package br.com.stringtracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateOpenGameRequest(
        @NotBlank @Size(max = 120) String place,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull @Min(1) @Max(8) Integer category,
        @Min(2) @Max(8) Integer capacity,
        /** Null = notifica toda a categoria; preenchido = só membros do grupo. */
        Long groupId
) {
}
