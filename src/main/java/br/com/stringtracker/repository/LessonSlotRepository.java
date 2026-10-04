package br.com.stringtracker.repository;

import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonSlotStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class LessonSlotRepository implements PanacheRepository<LessonSlot> {

    /** Horários do clube que começam entre {@code from} (inclusive) e {@code until} (exclusivo), sem os removidos. */
    public List<LessonSlot> listOfClub(long clubId, Instant from, Instant until) {
        return list("""
                        clubCoach.club.id = ?1 and startsAt >= ?2 and startsAt < ?3
                        and status <> ?4 and active = true order by startsAt
                        """,
                clubId, from, until, LessonSlotStatus.REMOVED);
    }

    /** Inícios dos horários do bloco entre {@code from} (inclusive) e {@code until} (exclusivo), em qualquer status. */
    public Set<Instant> startsAtOfBlock(long blockId, Instant from, Instant until) {
        return new HashSet<>(getEntityManager().createQuery("""
                        select s.startsAt from LessonSlot s
                        where s.scheduleBlock.id = :blockId and s.startsAt >= :from and s.startsAt < :until
                        """, Instant.class)
                .setParameter("blockId", blockId)
                .setParameter("from", from)
                .setParameter("until", until)
                .getResultList());
    }

    /**
     * Marca como removidos os horários do bloco que ainda não começaram e não têm reserva ativa
     * (segurada ou confirmada). Devolve quantos foram removidos.
     */
    public int removeFutureWithoutBooking(long blockId, Instant now) {
        return getEntityManager().createQuery("""
                        update LessonSlot s
                        set s.status = :removed, s.updateDate = :now
                        where s.scheduleBlock.id = :blockId and s.active = true
                          and s.startsAt > :now and s.status <> :removed
                          and not exists (
                              select 1 from Booking b
                              where b.lessonSlot = s and b.active = true and b.status in :activeBookings)
                        """)
                .setParameter("removed", LessonSlotStatus.REMOVED)
                .setParameter("now", now)
                .setParameter("blockId", blockId)
                .setParameter("activeBookings", List.of(BookingStatus.HELD, BookingStatus.CONFIRMED))
                .executeUpdate();
    }
}
