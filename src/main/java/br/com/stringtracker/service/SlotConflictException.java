package br.com.stringtracker.service;

/** Horário ou vaga já ocupados (restrição do banco violada). Vira HTTP 409. */
public class SlotConflictException extends RuntimeException {

    public SlotConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
