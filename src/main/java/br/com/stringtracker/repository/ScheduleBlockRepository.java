package br.com.stringtracker.repository;

import br.com.stringtracker.model.schedule.ScheduleBlock;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ScheduleBlockRepository implements PanacheRepository<ScheduleBlock> {

    // O filtro `active` é explícito: o Hibernate não aplica filtros herdados da BaseEntity (@MappedSuperclass).
    /** Blocos que ainda geram horários: ativos e com o professor ainda vinculado ao clube. */
    public List<Long> listGeneratingIds() {
        return getEntityManager().createQuery(
                        "select b.id from ScheduleBlock b where b.active = true and b.clubCoach.active = true order by b.id",
                        Long.class)
                .getResultList();
    }

    public Optional<ScheduleBlock> findActiveById(long id) {
        return find("id = ?1 and active = true", id).firstResultOptional();
    }

    public List<ScheduleBlock> listActiveOfLink(long clubCoachId) {
        return list("clubCoach.id = ?1 and active = true", clubCoachId);
    }
}
