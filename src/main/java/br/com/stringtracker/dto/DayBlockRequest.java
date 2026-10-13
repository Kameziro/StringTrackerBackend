package br.com.stringtracker.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Dia a bloquear. Sem {@code confirm} a chamada só lista as aulas afetadas; com {@code confirm} aplica o bloqueio. */
public record DayBlockRequest(@NotNull LocalDate date, boolean confirm) {
}
