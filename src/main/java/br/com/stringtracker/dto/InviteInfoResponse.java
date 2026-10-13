package br.com.stringtracker.dto;

import br.com.stringtracker.model.InviteKind;

import java.time.Instant;

public record InviteInfoResponse(InviteKind kind, long clubId, String clubName, Instant expiresAt) {
}
