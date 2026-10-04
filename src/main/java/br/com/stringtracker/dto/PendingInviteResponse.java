package br.com.stringtracker.dto;

import br.com.stringtracker.model.Invite;
import br.com.stringtracker.model.InviteKind;

import java.time.Instant;

/**
 * Convite ainda não aceito. O link nunca volta: o banco guarda só o hash do token, e quem perdeu o link reemite o
 * convite. {@code expired} marca o que já passou de 7 dias e só serve para reenviar.
 */
public record PendingInviteResponse(String email, InviteKind kind, Instant expiresAt, boolean expired) {

    public static PendingInviteResponse from(Invite invite, Instant now) {
        return new PendingInviteResponse(invite.getEmail(), invite.getKind(), invite.getExpiresAt(),
                invite.getExpiresAt().isBefore(now));
    }
}
