package br.com.stringtracker.service;

import jakarta.ws.rs.BadRequestException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/** Período de datas inclusivo nos dias do relógio da API (São Paulo), com limite de tamanho. */
public record DatePeriod(LocalDate from, LocalDate to) {

    public static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Data inválida: use o formato AAAA-MM-DD");
        }
    }

    /** 400 se {@code to} vier antes de {@code from} ou se o período passar de {@code maxDays} dias. */
    public static DatePeriod of(LocalDate from, LocalDate to, int maxDays) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= maxDays) {
            throw new BadRequestException("Período inválido: use até %d dias, com 'to' depois de 'from'".formatted(maxDays));
        }
        return new DatePeriod(from, to);
    }

    /** Primeiro instante do período. */
    public Instant start(Clock clock) {
        return from.atStartOfDay(clock.getZone()).toInstant();
    }

    /** Fim exclusivo: o primeiro instante depois do último dia. */
    public Instant end(Clock clock) {
        return to.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
    }
}
