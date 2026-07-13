package br.com.stringtracker.repository;

import br.com.stringtracker.model.Racket;
import br.com.stringtracker.model.User;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class RacketRepository implements PanacheRepository<Racket> {

    public List<Racket> findByUser(User user) {
        return list("user", user);
    }

    public long countByUser(User user) {
        return count("user", user);
    }

    public Optional<Racket> findOwnedBy(User user, Long racketId) {
        return find("id = ?1 and user = ?2", racketId, user).firstResultOptional();
    }
}
