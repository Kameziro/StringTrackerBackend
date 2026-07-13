package br.com.stringtracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "rackets")
@Getter
@Setter
@NoArgsConstructor
public class Racket extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String model;

    @Column(name = "tension_lbs", nullable = false)
    private double tensionLbs;

    @Column(name = "string_model", nullable = false)
    private String stringModel;

    @Column(name = "date_strung", nullable = false)
    private LocalDate dateStrung;

    @Setter(AccessLevel.NONE)
    @Column(name = "total_hours_played", nullable = false)
    private double totalHoursPlayed;

    public static Racket create(
            User owner,
            String brand,
            String model,
            String stringModel,
            double tensionLbs,
            LocalDate dateStrung
    ) {
        Racket racket = new Racket();
        racket.user = owner;
        racket.brand = brand;
        racket.model = model;
        racket.stringModel = stringModel;
        racket.tensionLbs = tensionLbs;
        racket.dateStrung = dateStrung;
        racket.totalHoursPlayed = 0.0;
        return racket;
    }

    public void addPlayMinutes(int durationMinutes) {
        this.totalHoursPlayed += durationMinutes / 60.0;
    }
}
