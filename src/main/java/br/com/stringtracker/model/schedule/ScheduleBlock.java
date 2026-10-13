package br.com.stringtracker.model.schedule;

import br.com.stringtracker.model.BaseEntity;
import br.com.stringtracker.model.ClubCoach;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/** Bloco semanal recorrente de um professor num clube; gera os {@link LessonSlot}. Horários em America/Sao_Paulo. */
@Entity
@Table(name = "schedule_blocks")
@Getter
@Setter
@NoArgsConstructor
public class ScheduleBlock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_coach_id", nullable = false)
    private ClubCoach clubCoach;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private LessonKind kind;

    /** 1 (segunda) a 7 (domingo). */
    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "duration_minutes", nullable = false)
    private short durationMinutes;

    @Column(nullable = false)
    private short capacity = 1;

    @Column(length = 80)
    private String title;

    public static ScheduleBlock create(ClubCoach clubCoach, LessonKind kind, short dayOfWeek,
                                       LocalTime startTime, LocalTime endTime, short durationMinutes,
                                       short capacity) {
        ScheduleBlock block = new ScheduleBlock();
        block.clubCoach = clubCoach;
        block.kind = kind;
        block.dayOfWeek = dayOfWeek;
        block.startTime = startTime;
        block.endTime = endTime;
        block.durationMinutes = durationMinutes;
        block.capacity = capacity;
        return block;
    }
}
