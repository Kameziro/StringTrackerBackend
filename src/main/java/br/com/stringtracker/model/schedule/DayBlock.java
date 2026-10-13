package br.com.stringtracker.model.schedule;

import br.com.stringtracker.model.BaseEntity;
import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Dia bloqueado de um professor. {@code club} nulo vale para todos os clubes dele. */
@Entity
@Table(name = "day_blocks")
@Getter
@Setter
@NoArgsConstructor
public class DayBlock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private Coach coach;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id")
    private Club club;

    @Column(nullable = false)
    private LocalDate day;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    public static DayBlock create(Coach coach, Club club, LocalDate day, User createdBy) {
        DayBlock block = new DayBlock();
        block.coach = coach;
        block.club = club;
        block.day = day;
        block.createdBy = createdBy;
        return block;
    }
}
