package br.com.stringtracker.repository;

import br.com.stringtracker.model.Coach;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class CoachRepository implements PanacheRepository<Coach> {

    public boolean existsByIdAndUserId(long coachId, long userId) {
        return count("id = ?1 and user.id = ?2", coachId, userId) > 0;
    }

    public Optional<Coach> findByUserId(long userId) {
        return find("user.id", userId).firstResultOptional();
    }
}
