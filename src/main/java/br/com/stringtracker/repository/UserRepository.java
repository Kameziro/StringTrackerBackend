package br.com.stringtracker.repository;

import br.com.stringtracker.model.User;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.Optional;

@ApplicationScoped
public class UserRepository implements PanacheRepository<User> {

    public Optional<User> findByKeycloakId(String keycloakId) {
        return find("keycloakId", keycloakId).firstResultOptional();
    }

    public User lockById(Long id) {
        return getEntityManager().find(User.class, id, LockModeType.PESSIMISTIC_WRITE);
    }
}
