package br.com.stringtracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAvailabilityRequest {

    @NotNull
    @Valid
    private List<AvailabilitySlotRequest> slots;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailabilitySlotRequest {

        @NotNull
        @Min(1)
        @Max(7)
        private Integer dayOfWeek;

        @NotNull
        private LocalTime startTime;

        @NotNull
        private LocalTime endTime;

        private Long clubId;
    }
}
