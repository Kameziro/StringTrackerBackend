package br.com.stringtracker.dto;

import br.com.stringtracker.model.InviteKind;

public record InviteAcceptedResponse(InviteKind kind, long clubId, String clubName) {
}
