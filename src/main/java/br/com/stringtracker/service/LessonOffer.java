package br.com.stringtracker.service;

import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonType;

/** Regra comum às reservas por Pix e manuais: o tipo de aula tem que caber no horário e ser oferecido pelo professor. */
final class LessonOffer {

    private LessonOffer() {
    }

    /** O vínculo do professor com o clube do horário, ou 422 se o tipo não vale nesse horário com esse professor. */
    static ClubCoach requireOffered(LessonSlot slot, LessonType type) {
        ClubCoach link = slot.getClubCoach();
        if (!type.fits(slot.getKind()) || !link.getCoach().offers(type)) {
            throw new BusinessRuleException("O professor não oferece esse tipo de aula neste horário");
        }
        return link;
    }
}
