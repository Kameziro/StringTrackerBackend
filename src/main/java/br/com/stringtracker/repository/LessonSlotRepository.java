package br.com.stringtracker.repository;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonSlotStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class LessonSlotRepository implements PanacheRepository<LessonSlot> {

    /**
     * Horário que um aluno pode reservar: aberto, com vaga sobrando e começando a partir de {@code :from} (a
     * antecedência mínima da reserva), num vínculo e num clube ativos.
     */
    private static final String BOOKABLE = """
            s.active = true and s.status = :open and s.startsAt >= :from
            and s.clubCoach.active = true and s.clubCoach.club.active = true
            and s.capacity > (select count(b) from Booking b
                              where b.lessonSlot = s and b.active = true and b.status in :seatStatuses)
            """;

    private static final List<BookingStatus> SEAT_STATUSES = List.of(BookingStatus.HELD, BookingStatus.CONFIRMED);

    /** Clube da cidade com ao menos um horário reservável e o início do primeiro deles. */
    public record ClubAvailability(Club club, Instant nextFreeAt) {
    }

    /** Clubes ativos da cidade com horário reservável a partir de {@code from}, por nome. */
    public List<ClubAvailability> listBookableClubsOfCity(long cityId, Instant from) {
        return getEntityManager().createQuery("""
                        select c, min(s.startsAt) from LessonSlot s join s.clubCoach cc join cc.club c
                        where c.city.id = :cityId and %s
                        group by c order by c.name, c.id
                        """.formatted(BOOKABLE), Object[].class)
                .setParameter("cityId", cityId)
                .setParameter("open", LessonSlotStatus.OPEN)
                .setParameter("from", from)
                .setParameter("seatStatuses", SEAT_STATUSES)
                .getResultList().stream()
                .map(row -> new ClubAvailability((Club) row[0], (Instant) row[1]))
                .toList();
    }

    /** Horários reserváveis do clube a partir de {@code from}, com o professor e o usuário dele, do mais próximo. */
    public List<LessonSlot> listBookableOfClub(long clubId, Instant from) {
        return getEntityManager().createQuery("""
                        select s from LessonSlot s join fetch s.coach co join fetch co.user
                        where s.clubCoach.club.id = :clubId and %s
                        order by s.startsAt, s.id
                        """.formatted(BOOKABLE), LessonSlot.class)
                .setParameter("clubId", clubId)
                .setParameter("open", LessonSlotStatus.OPEN)
                .setParameter("from", from)
                .setParameter("seatStatuses", SEAT_STATUSES)
                .getResultList();
    }

    /** Trava a agenda do professor até o fim da transação; a linha do professor é o lock de todos os clubes dele. */
    public void lockCoachSchedule(Coach coach) {
        getEntityManager().lock(coach, LockModeType.PESSIMISTIC_WRITE);
    }

    /** Horários do clube que começam entre {@code from} (inclusive) e {@code until} (exclusivo), sem os removidos. */
    public List<LessonSlot> listOfClub(long clubId, Instant from, Instant until) {
        return list("""
                        clubCoach.club.id = ?1 and startsAt >= ?2 and startsAt < ?3
                        and status <> ?4 and active = true order by startsAt
                        """,
                clubId, from, until, LessonSlotStatus.REMOVED);
    }

    /**
     * Horários do professor que começam entre {@code dayStart} (inclusive) e {@code dayEnd} (exclusivo) e depois de
     * {@code now}, sem os removidos, por id. {@code clubId} nulo vale para todos os clubes do professor.
     * Com {@code lock} diferente de NONE as linhas ficam travadas até o fim da transação.
     */
    public List<LessonSlot> listUpcomingOfCoachOnDay(long coachId, Long clubId, Instant dayStart, Instant dayEnd,
                                                     Instant now, LockModeType lock) {
        String clubFilter = clubId == null ? "" : "and s.clubCoach.club.id = :clubId";
        TypedQuery<LessonSlot> query = getEntityManager().createQuery("""
                select s from LessonSlot s
                where s.coach.id = :coachId and s.startsAt >= :dayStart and s.startsAt < :dayEnd
                  and s.startsAt > :now and s.status <> :removed and s.active = true
                  %s
                order by s.id
                """.formatted(clubFilter), LessonSlot.class)
                .setParameter("coachId", coachId)
                .setParameter("dayStart", dayStart)
                .setParameter("dayEnd", dayEnd)
                .setParameter("now", now)
                .setParameter("removed", LessonSlotStatus.REMOVED)
                .setLockMode(lock);
        if (clubId != null) {
            query.setParameter("clubId", clubId);
        }
        return query.getResultList();
    }

    /**
     * Horários do vínculo que ainda não começaram, sem os removidos, por id. Com {@code lock} diferente de NONE as
     * linhas ficam travadas até o fim da transação.
     */
    public List<LessonSlot> listUpcomingOfLink(long clubCoachId, Instant now, LockModeType lock) {
        return getEntityManager().createQuery("""
                        select s from LessonSlot s
                        where s.clubCoach.id = :clubCoachId and s.startsAt > :now
                          and s.status <> :removed and s.active = true
                        order by s.id
                        """, LessonSlot.class)
                .setParameter("clubCoachId", clubCoachId)
                .setParameter("now", now)
                .setParameter("removed", LessonSlotStatus.REMOVED)
                .setLockMode(lock)
                .getResultList();
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
