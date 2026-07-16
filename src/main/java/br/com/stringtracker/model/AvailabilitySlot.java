package br.com.stringtracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "availability_slots")
@Getter
@Setter
@NoArgsConstructor
public class AvailabilitySlot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** ISO-8601: 1 = Monday … 7 = Sunday */
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id")
    private Club club;

    public static AvailabilitySlot create(
            User user,
            int dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            Club club
    ) {
        AvailabilitySlot slot = new AvailabilitySlot();
        slot.user = user;
        slot.dayOfWeek = dayOfWeek;
        slot.startTime = startTime;
        slot.endTime = endTime;
        slot.club = club;
        return slot;
    }
}
