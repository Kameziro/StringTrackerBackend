package br.com.stringtracker.dto;

import java.util.List;

/**
 * Resultado de desvincular um professor do clube. {@code applied = false} é a pré-visualização: nada mudou e
 * {@code affected} lista as aulas futuras que seriam canceladas; com {@code applied = true} elas vêm canceladas.
 */
public record UnlinkCoachResponse(boolean applied, List<AffectedBookingResponse> affected) {
}
