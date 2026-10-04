package br.com.stringtracker.repository;

import br.com.stringtracker.model.schedule.DayBlock;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class DayBlockRepository implements PanacheRepository<DayBlock> {

    /** Dias de {@code from} (inclusive) a {@code until} (exclusivo) em que o professor está bloqueado naquele clube. */
    public Set<LocalDate> blockedDays(long coachId, long clubId, LocalDate from, LocalDate until) {
        return new HashSet<>(getEntityManager().createQuery("""
                        select d.day from DayBlock d
                        where d.coach.id = :coachId and d.active = true
                          and (d.club is null or d.club.id = :clubId)
                          and d.day >= :from and d.day < :until
                        """, LocalDate.class)
                .setParameter("coachId", coachId)
                .setParameter("clubId", clubId)
                .setParameter("from", from)
                .setParameter("until", until)
                .getResultList());
    }

    /** Se o professor já tem esse dia bloqueado com o mesmo alcance: um clube ({@code clubId}) ou todos (nulo). */
    public boolean exists(long coachId, Long clubId, LocalDate day) {
        if (clubId == null) {
            return count("coach.id = ?1 and day = ?2 and club is null and active = true", coachId, day) > 0;
        }
        return count("coach.id = ?1 and day = ?2 and club.id = ?3 and active = true", coachId, day, clubId) > 0;
    }
}
