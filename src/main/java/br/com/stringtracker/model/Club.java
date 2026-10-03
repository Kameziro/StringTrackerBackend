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

@Entity
@Table(name = "clubs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_clubs_name", columnNames = "name")
})
@Getter
@Setter
@NoArgsConstructor
public class Club extends BaseEntity {

    @Column(nullable = false)
    private String name;

    /** Nula nos clubes criados antes da agenda (seed e nomes livres de jogos). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @Column
    private String address;

    @Column(length = 20)
    private String whatsapp;

    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 16)
    private ClubPaymentStatus paymentStatus = ClubPaymentStatus.NOT_CONNECTED;

    @Column(name = "mp_user_id", length = 32)
    private String mpUserId;

    /** Token OAuth do Mercado Pago cifrado com {@code TokenCipher}. Nunca sai da API. */
    @Column(name = "mp_access_token_enc", columnDefinition = "text")
    private String mpAccessTokenEnc;

    /** Refresh token do Mercado Pago cifrado com {@code TokenCipher}. Nunca sai da API. */
    @Column(name = "mp_refresh_token_enc", columnDefinition = "text")
    private String mpRefreshTokenEnc;

    @Column(name = "mp_token_expires_at")
    private Instant mpTokenExpiresAt;

    public static Club create(String name) {
        Club club = new Club();
        club.name = name;
        return club;
    }
}
