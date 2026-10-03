package br.com.stringtracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Convite por e-mail. O token nunca é guardado, só o hash SHA-256 em hexadecimal. */
@Entity
@Table(name = "invites", uniqueConstraints = {
        @UniqueConstraint(name = "uk_invites_token_hash", columnNames = "token_hash")
})
@Getter
@Setter
@NoArgsConstructor
public class Invite extends BaseEntity {

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InviteKind kind;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by")
    private User invitedBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_by")
    private User acceptedBy;

    public static Invite create(String tokenHash, String email, InviteKind kind, Club club,
                                User invitedBy, Instant expiresAt) {
        Invite invite = new Invite();
        invite.tokenHash = tokenHash;
        invite.email = email;
        invite.kind = kind;
        invite.club = club;
        invite.invitedBy = invitedBy;
        invite.expiresAt = expiresAt;
        return invite;
    }
}
