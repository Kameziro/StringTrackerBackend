package br.com.stringtracker.service;

/** Convite existe mas não pode mais ser usado (expirado ou já aceito). Vira HTTP 410. */
public class InviteUnavailableException extends RuntimeException {

    public InviteUnavailableException(String message) {
        super(message);
    }
}
