package br.com.stringtracker.dto;

import br.com.stringtracker.model.schedule.LessonKind;
import br.com.stringtracker.model.schedule.LessonSlot;

import java.time.Instant;

/** Horário livre que o aluno pode reservar. */
public record LessonSlotOption(long slotId, Instant startsAt, Instant endsAt, LessonKind kind) {

    public static LessonSlotOption from(LessonSlot slot) {
        return new LessonSlotOption(slot.getId(), slot.getStartsAt(), slot.getEndsAt(), slot.getKind());
    }
}
