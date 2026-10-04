package br.com.stringtracker.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Resultado do bloqueio de um dia. {@code applied = false} é a pré-visualização: nada mudou e {@code affected}
 * lista as reservas que o bloqueio cancelaria. Com {@code applied = true}, {@code affected} traz as reservas
 * canceladas, com o reembolso de cada uma.
 */
public record DayBlockResponse(LocalDate date, boolean applied, List<AffectedBookingResponse> affected) {
}
