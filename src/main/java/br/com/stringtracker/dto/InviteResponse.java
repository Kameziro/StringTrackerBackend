package br.com.stringtracker.dto;

import br.com.stringtracker.model.InviteKind;

import java.time.Instant;

/** Convite recém-emitido; {@code link} só existe aqui, o banco guarda apenas o hash do token. */
public record InviteResponse(String email, InviteKind kind, Instant expiresAt, String link) {
}
