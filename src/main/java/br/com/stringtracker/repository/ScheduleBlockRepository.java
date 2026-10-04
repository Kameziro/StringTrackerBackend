package br.com.stringtracker.repository;

import br.com.stringtracker.model.schedule.ScheduleBlock;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class ScheduleBlockRepository implements PanacheRepository<ScheduleBlock> {

    // O filtro `active` é explícito: o Hibernate não aplica filtros herdados da BaseEntity (@MappedSuperclass).
    public Optional<ScheduleBlock> findActiveById(long id) {
        return find("id = ?1 and active = true", id).firstResultOptional();
    }
}
