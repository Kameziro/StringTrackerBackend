package br.com.stringtracker.model.schedule;

import br.com.stringtracker.model.BaseEntity;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.Coach;
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

import java.time.Instant;

/**
 * Ocorrência concreta de aula. O banco impede que o mesmo professor tenha dois slots
 * não removidos sobrepostos, em qualquer clube.
 */
@Entity
@Table(name = "lesson_slots")
@Getter
@Setter
@NoArgsConstructor
public class LessonSlot extends BaseEntity {

    /** Null para horários criados fora de um bloco recorrente. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_block_id")
    private ScheduleBlock scheduleBlock;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_coach_id", nullable = false)
    private ClubCoach clubCoach;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private Coach coach;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private LessonKind kind;

    @Column(nullable = false)
    private short capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LessonSlotStatus status = LessonSlotStatus.OPEN;

    public static LessonSlot create(ScheduleBlock scheduleBlock, ClubCoach clubCoach, Instant startsAt,
                                    Instant endsAt, LessonKind kind, short capacity) {
        LessonSlot slot = new LessonSlot();
        slot.scheduleBlock = scheduleBlock;
        slot.clubCoach = clubCoach;
        slot.coach = clubCoach.getCoach();
        slot.startsAt = startsAt;
        slot.endsAt = endsAt;
        slot.kind = kind;
        slot.capacity = capacity;
        return slot;
    }
}
