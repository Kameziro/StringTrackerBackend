package br.com.stringtracker.repository;

import br.com.stringtracker.model.schedule.ScheduleBlock;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ScheduleBlockRepository implements PanacheRepository<ScheduleBlock> {
}
