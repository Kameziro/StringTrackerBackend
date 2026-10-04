package br.com.stringtracker.repository;

import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class BookingRepository implements PanacheRepository<Booking> {

    /** Reserva ativa (segurada ou confirmada) ocupando uma vaga de um horário. */
    public record ActiveSeat(long slotId, BookingStatus status) {
    }

    /** Vagas ocupadas dos horários do clube que começam entre {@code from} (inclusive) e {@code until} (exclusivo). */
    public List<ActiveSeat> listActiveSeats(long clubId, Instant from, Instant until) {
        return getEntityManager().createQuery("""
                        select b.lessonSlot.id, b.status
                        from Booking b
                        where b.lessonSlot.clubCoach.club.id = :clubId
                          and b.lessonSlot.startsAt >= :from and b.lessonSlot.startsAt < :until
                          and b.active = true and b.status in :activeStatuses
                        """, Object[].class)
                .setParameter("clubId", clubId)
                .setParameter("from", from)
                .setParameter("until", until)
                .setParameter("activeStatuses", List.of(BookingStatus.HELD, BookingStatus.CONFIRMED))
                .getResultList().stream()
                .map(row -> new ActiveSeat((Long) row[0], (BookingStatus) row[1]))
                .toList();
    }
}
