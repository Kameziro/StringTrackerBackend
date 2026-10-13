package br.com.stringtracker.model.schedule;

/** Situação de um horário para quem o consulta: livre, segurado (Pix pendente), reservado ou bloqueado. */
public enum SlotAvailability {
    FREE,
    HELD,
    BOOKED,
    BLOCKED;

    /** {@code held} e {@code confirmed} contam as reservas ativas; sobra vaga enquanto elas forem menos que a capacidade. */
    public static SlotAvailability of(LessonSlot slot, long held, long confirmed) {
        if (slot.getStatus() == LessonSlotStatus.BLOCKED) {
            return BLOCKED;
        }
        if (held + confirmed < slot.getCapacity()) {
            return FREE;
        }
        return confirmed > 0 ? BOOKED : HELD;
    }
}
