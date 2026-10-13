package br.com.stringtracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/** Bloco semanal de aula particular: dia (1 = segunda a 7 = domingo), janela de horário e duração (60 ou 90). */
public record CreateScheduleBlockRequest(
        @Min(1) @Max(7) int dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        int durationMinutes
) {
}
