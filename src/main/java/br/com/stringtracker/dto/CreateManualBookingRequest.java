package br.com.stringtracker.dto;

import br.com.stringtracker.model.schedule.LessonType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Reserva manual (paga por fora) de um horário livre. O aluno é {@code studentUserId} (conta encontrada na busca)
 * ou, sem conta, {@code guestName} e {@code guestPhone}; nunca os dois. {@code partnerName} só vale em duplas.
 */
public record CreateManualBookingRequest(
        @NotNull Long slotId,
        @NotNull LessonType type,
        Long studentUserId,
        @Size(max = 120) String guestName,
        @Size(max = 32) String guestPhone,
        @Size(max = 120) String partnerName
) {
}
