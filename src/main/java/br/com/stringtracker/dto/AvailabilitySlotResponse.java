package br.com.stringtracker.dto;

import br.com.stringtracker.model.AvailabilitySlot;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilitySlotResponse {

    private Long id;
    private int dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long clubId;
    private String clubName;

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
