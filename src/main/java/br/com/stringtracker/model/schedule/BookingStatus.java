package br.com.stringtracker.model.schedule;

public enum BookingStatus {
    HELD,
    CONFIRMED,
    EXPIRED,
    CANCELLED;

    /** Segurada ou confirmada: ocupa uma vaga do horário e ainda pode ser cancelada. */
    public boolean holdsSeat() {
        return this == HELD || this == CONFIRMED;
    }
}
