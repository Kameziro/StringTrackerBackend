package br.com.stringtracker.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;
import java.time.ZoneId;

/** Relógio único da agenda: dias e horários dos blocos valem em America/Sao_Paulo. Testes o substituem por um relógio fixo. */
@ApplicationScoped
public class ClockProducer {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Produces
    @ApplicationScoped
    Clock clock() {
        return Clock.system(ZONE);
    }
}
