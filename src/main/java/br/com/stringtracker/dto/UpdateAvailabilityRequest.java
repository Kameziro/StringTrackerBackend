package br.com.stringtracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;

public record UpdateAvailabilityRequest(
        @NotNull @Valid List<AvailabilitySlotRequest> slots
) {
    public record AvailabilitySlotRequest(
            @NotNull @Min(1) @Max(7) Integer dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            Long clubId
    ) {
    }
}
