package br.com.stringtracker.dto;

import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.schedule.LessonKind;
import br.com.stringtracker.model.schedule.LessonType;

import java.util.Arrays;
import java.util.List;

/** Tipo de aula que o aluno pode reservar com o professor num clube, com o preço do clube em centavos. */
public record LessonTypeOption(LessonType type, long priceCents) {

    /**
     * Tipos de aula particular que o professor oferece e que o clube já precificou. Turma (grupo) fica de fora:
     * ela só vale em horários de turma, que têm listagem própria.
     */
    public static List<LessonTypeOption> offeredBy(ClubCoach link) {
        return Arrays.stream(LessonType.values())
                .filter(type -> type.fits(LessonKind.PRIVATE))
                .filter(type -> link.getCoach().offers(type) && link.priceOf(type) != null)
                .map(type -> new LessonTypeOption(type, link.priceOf(type)))
                .toList();
    }
}
