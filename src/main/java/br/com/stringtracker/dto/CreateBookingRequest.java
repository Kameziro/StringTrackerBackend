package br.com.stringtracker.dto;

import br.com.stringtracker.model.schedule.LessonType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code partnerName} só vale em aula de duplas (opcional, até 120 caracteres). */
public record CreateBookingRequest(
        @NotNull Long slotId,
        @NotNull LessonType type,
        @Size(max = 120) String partnerName
) {
}
