package br.com.stringtracker.dto;

import br.com.stringtracker.model.schedule.ScheduleBlock;

import java.time.LocalTime;
import java.util.List;

/** Blocos semanais ativos do clube, com o nome do professor, para o painel listar e remover. */
public record ClubBlocksResponse(List<Block> blocks) {

    public record Block(
            long id,
            long coachId,
            String coachName,
            int dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            int durationMinutes
    ) {

        public static Block from(ScheduleBlock block) {
            var coach = block.getClubCoach().getCoach();
            return new Block(block.getId(), coach.getId(), coach.getUser().getName(), block.getDayOfWeek(),
                    block.getStartTime(), block.getEndTime(), block.getDurationMinutes());
        }
    }
}
