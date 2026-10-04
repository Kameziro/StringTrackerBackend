package br.com.stringtracker.model;

import br.com.stringtracker.model.schedule.LessonType;
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

/** Vínculo entre professor e clube; carrega a tabela de preços (em centavos) daquele clube. */
@Entity
@Table(name = "club_coaches", uniqueConstraints = {
        @UniqueConstraint(name = "uk_club_coaches_club_coach", columnNames = {"club_id", "coach_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class ClubCoach extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private Coach coach;

    @Column(name = "price_singles_cents")
    private Long priceSinglesCents;

    @Column(name = "price_doubles_cents")
    private Long priceDoublesCents;

    @Column(name = "price_group_cents")
    private Long priceGroupCents;

    /** Preço em centavos do tipo de aula neste clube; nulo se o clube ainda não definiu. */
    public Long priceOf(LessonType type) {
        return switch (type) {
            case SINGLES -> priceSinglesCents;
            case DOUBLES -> priceDoublesCents;
            case GROUP -> priceGroupCents;
        };
    }

    public static ClubCoach create(Club club, Coach coach) {
        ClubCoach link = new ClubCoach();
        link.club = club;
        link.coach = coach;
        return link;
    }
}
