package br.com.stringtracker.repository;

import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class BookingRepository implements PanacheRepository<Booking> {

    private static final List<BookingStatus> ACTIVE = List.of(BookingStatus.HELD, BookingStatus.CONFIRMED);

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
                .setParameter("activeStatuses", ACTIVE)
                .getResultList().stream()
                .map(row -> new ActiveSeat((Long) row[0], (BookingStatus) row[1]))
                .toList();
    }

    // O filtro `active` é explícito: o Hibernate não aplica filtros herdados da BaseEntity (@MappedSuperclass).
    public Optional<Booking> findByIdAndStudent(long bookingId, long studentUserId) {
        return find("id = ?1 and studentUser.id = ?2 and active = true", bookingId, studentUserId)
                .firstResultOptional();
    }

    /** Como {@link #findByIdAndStudent}, travando a linha da reserva até o fim da transação. */
    public Optional<Booking> findByIdAndStudentForUpdate(long bookingId, long studentUserId) {
        return find("id = ?1 and studentUser.id = ?2 and active = true", bookingId, studentUserId)
                .withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    /** Reserva ativa travada até o fim da transação: é o lock do agregado antes de mexer no pagamento. */
    public Optional<Booking> findActiveForUpdate(long bookingId) {
        return find("id = ?1 and active = true", bookingId)
                .withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    /** Reservas ativas (seguradas ou confirmadas) dos horários dados, com horário, clube e aluno, na ordem de criação. */
    public List<Booking> listActiveOfSlots(Collection<Long> slotIds) {
        if (slotIds.isEmpty()) {
            return List.of();
        }
        return getEntityManager().createQuery("""
                        select b from Booking b
                        join fetch b.lessonSlot s join fetch s.clubCoach cc join fetch cc.club
                        left join fetch b.studentUser
                        where s.id in :slotIds and b.active = true and b.status in :activeStatuses
                        order by b.id
                        """, Booking.class)
                .setParameter("slotIds", slotIds)
                .setParameter("activeStatuses", ACTIVE)
                .getResultList();
    }

    /**
     * Reservas ativas de qualquer estado dos horários do clube que começam entre {@code from} (inclusive) e
     * {@code until} (exclusivo), com horário, professor e aluno carregados, por início do horário.
     */
    public List<Booking> listOfClub(long clubId, Instant from, Instant until) {
        return getEntityManager().createQuery("""
                        select b from Booking b
                        join fetch b.lessonSlot s join fetch s.coach co join fetch co.user
                        left join fetch b.studentUser
                        where s.clubCoach.club.id = :clubId and s.startsAt >= :from and s.startsAt < :until
                          and b.active = true
                        order by s.startsAt, b.id
                        """, Booking.class)
                .setParameter("clubId", clubId)
                .setParameter("from", from)
                .setParameter("until", until)
                .getResultList();
    }

    /**
     * Aulas do aluno: reservas confirmadas e canceladas (as seguradas e as expiradas nunca viraram aula), com horário,
     * clube e professor, da aula mais próxima para a mais distante. Inclui as manuais vinculadas à conta dele.
     */
    public List<Booking> listLessonsOfStudent(long studentUserId) {
        return getEntityManager().createQuery("""
                        select b from Booking b
                        join fetch b.lessonSlot s join fetch s.clubCoach cc join fetch cc.club
                        join fetch s.coach co join fetch co.user
                        where b.studentUser.id = :studentId and b.active = true and b.status in :lessonStatuses
                        order by s.startsAt, b.id
                        """, Booking.class)
                .setParameter("studentId", studentUserId)
                .setParameter("lessonStatuses", List.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED))
                .getResultList();
    }

    /** Ids das reservas ativas (seguradas ou confirmadas) dos horários dados, na ordem em que foram criadas. */
    public List<Long> listActiveIdsOfSlots(Collection<Long> slotIds) {
        if (slotIds.isEmpty()) {
            return List.of();
        }
        return getEntityManager().createQuery("""
                        select b.id from Booking b
                        where b.lessonSlot.id in :slotIds and b.active = true and b.status in :activeStatuses
                        order by b.id
                        """, Long.class)
                .setParameter("slotIds", slotIds)
                .setParameter("activeStatuses", ACTIVE)
                .getResultList();
    }

    /** Ids das reservas seguradas cujo hold já venceu, as mais antigas primeiro. */
    public List<Long> listExpiredHoldIds(Instant now) {
        return getEntityManager().createQuery("""
                        select b.id from Booking b
                        where b.status = :held and b.active = true and b.holdExpiresAt < :now
                        order by b.holdExpiresAt
                        """, Long.class)
                .setParameter("held", BookingStatus.HELD)
                .setParameter("now", now)
                .getResultList();
    }

    /** Números das vagas do horário ocupadas por reserva ativa. */
    public Set<Short> occupiedSeats(long slotId) {
        return new HashSet<>(getEntityManager().createQuery("""
                        select b.seat from Booking b
                        where b.lessonSlot.id = :slotId and b.active = true and b.status in :activeStatuses
                        """, Short.class)
                .setParameter("slotId", slotId)
                .setParameter("activeStatuses", ACTIVE)
                .getResultList());
    }
}
