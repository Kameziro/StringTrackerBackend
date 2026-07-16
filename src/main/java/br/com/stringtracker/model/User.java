package br.com.stringtracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_keycloak_id", columnNames = "keycloak_id"),
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(name = "keycloak_id", nullable = false, length = 64)
    private String keycloakId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    /** Categoria de padel (1–8). Null até o jogador completar o perfil. */
    @Column
    private Integer category;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "city_id")
    private City city;

    @Column(name = "available_today", nullable = false)
    private boolean availableToday;

    @Column(name = "available_today_at")
    private Instant availableTodayAt;
}
