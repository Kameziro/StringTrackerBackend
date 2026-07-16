package br.com.stringtracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @Min(1) @Max(8) Integer category,
        @NotNull Long cityId,
        @NotNull Boolean availableToday
) {
}
