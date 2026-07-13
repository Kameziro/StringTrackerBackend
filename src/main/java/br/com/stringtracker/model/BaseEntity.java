package br.com.stringtracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/**
 * Campos comuns a todas as entidades persistidas (soft-delete + auditoria).
 * {@link SQLRestriction} aplica filtro {@code active = true} em todas as queries.
 */
@MappedSuperclass
@SQLRestriction("active = true")
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "registration_date", nullable = false)
    private Instant registrationDate;

    @Column(name = "update_date", nullable = false)
    private Instant updateDate;

    @Column(name = "exclusion_date")
    private Instant exclusionDate;

    @PrePersist
    protected void onPersist() {
        Instant now = Instant.now();
        if (registrationDate == null) {
            registrationDate = now;
        }
        updateDate = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updateDate = Instant.now();
    }

    /** Soft-delete: desativa e carimba exclusion_date. */
    public void markExcluded() {
        this.active = false;
        this.exclusionDate = Instant.now();
    }
}
