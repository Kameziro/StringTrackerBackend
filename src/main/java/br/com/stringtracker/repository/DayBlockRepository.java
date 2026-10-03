package br.com.stringtracker.repository;

import br.com.stringtracker.model.schedule.DayBlock;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DayBlockRepository implements PanacheRepository<DayBlock> {
}
