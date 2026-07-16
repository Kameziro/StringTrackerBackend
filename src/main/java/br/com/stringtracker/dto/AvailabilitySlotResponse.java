package br.com.stringtracker.dto;

import br.com.stringtracker.model.AvailabilitySlot;

import java.time.LocalTime;

public record AvailabilitySlotResponse(
        Long id,
        int dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Long clubId,
        String clubName
) {
    public static AvailabilitySlotResponse from(AvailabilitySlot slot) {
        Long clubId = slot.getClub() != null ? slot.getClub().getId() : null;
        String clubName = slot.getClub() != null ? slot.getClub().getName() : null;
        return new AvailabilitySlotResponse(
                slot.getId(),
                slot.getDayOfWeek(),
                slot.getStartTime(),
                slot.getEndTime(),
                clubId,
                clubName
        );
    }
}
