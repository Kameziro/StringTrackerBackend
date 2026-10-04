package br.com.stringtracker.dto;

import br.com.stringtracker.model.schedule.ScheduleBlock;

import java.time.LocalTime;

public record ScheduleBlockResponse(
        long id,
        long coachId,
        int dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        int durationMinutes,
        int slotsCreated
) {

    public static ScheduleBlockResponse from(ScheduleBlock block, int slotsCreated) {
        return new ScheduleBlockResponse(
                block.getId(),
                block.getClubCoach().getCoach().getId(),
                block.getDayOfWeek(),
                block.getStartTime(),
                block.getEndTime(),
                block.getDurationMinutes(),
                slotsCreated);
    }
}
