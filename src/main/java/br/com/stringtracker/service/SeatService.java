package br.com.stringtracker.service;

import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonSlotStatus;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.LessonSlotRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.NotFoundException;

import java.sql.SQLException;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Ocupação das vagas (seats) de um horário, comum à reserva paga com Pix e à reserva manual. O horário é travado
 * (FOR UPDATE) enquanto a vaga é escolhida, e o índice único de vaga do banco (V13) continua sendo a garantia final.
 */
@ApplicationScoped
public class SeatService {

    private static final String UNIQUE_VIOLATION = "23505";

    @Inject
    LessonSlotRepository lessonSlotRepository;

    @Inject
    BookingRepository bookingRepository;

    /** Trava o horário até o fim da transação; 404 se ele não existe, foi removido ou o professor saiu do clube. */
    public LessonSlot lockSlot(long slotId) {
        LessonSlot slot = lessonSlotRepository.findById(slotId, LockModeType.PESSIMISTIC_WRITE);
        if (slot == null || !slot.isActive() || slot.getStatus() == LessonSlotStatus.REMOVED
                || !slot.getClubCoach().isActive() || !slot.getClubCoach().getClub().isActive()) {
            throw new NotFoundException("Horário não encontrado");
        }
        return slot;
    }

    /** Menor vaga livre do horário; sem nenhuma, {@link SlotConflictException} com {@code takenMessage}. */
    public short freeSeat(LessonSlot slot, String takenMessage) {
        Set<Short> occupied = bookingRepository.occupiedSeats(slot.getId());
        return IntStream.rangeClosed(1, slot.getCapacity())
                .filter(seat -> !occupied.contains((short) seat))
                .mapToObj(seat -> (short) seat)
                .findFirst()
                .orElseThrow(() -> new SlotConflictException(takenMessage));
    }

    /** Grava a reserva; se o índice único recusar a vaga, {@link SlotConflictException} com {@code takenMessage}. */
    public void persist(Booking booking, String takenMessage) {
        try {
            bookingRepository.persist(booking);
        } catch (PersistenceException e) {
            if (isUniqueViolation(e)) {
                throw new SlotConflictException(takenMessage, e);
            }
            throw e;
        }
    }

    private static boolean isUniqueViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
