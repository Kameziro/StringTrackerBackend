package br.com.stringtracker.dto;

import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.schedule.LessonKind;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.SlotAvailability;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Grade semanal do clube: os professores vinculados e os horários de segunda ({@code weekStart}) a domingo. */
public record AgendaResponse(LocalDate weekStart, LocalDate weekEnd, List<Coach> coaches, List<Slot> slots) {

    public record Coach(long coachId, String name) {

        public static Coach from(ClubCoach link) {
            return new Coach(link.getCoach().getId(), link.getCoach().getUser().getName());
        }
    }

    public record Slot(long id, long coachId, Instant startsAt, Instant endsAt, LessonKind kind, int capacity,
                       SlotAvailability status) {

        public static Slot from(LessonSlot slot, SlotAvailability status) {
            return new Slot(slot.getId(), slot.getCoach().getId(), slot.getStartsAt(), slot.getEndsAt(),
                    slot.getKind(), slot.getCapacity(), status);
        }
    }
}
