package br.com.stringtracker.repository;

import br.com.stringtracker.model.User;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class UserRepository implements PanacheRepository<User> {

    public Optional<User> findByKeycloakId(String keycloakId) {
        return find("keycloakId", keycloakId).firstResultOptional();
    }

    public Optional<User> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }

    public List<User> findAvailableTodayByCategoryAndCity(int category, Long cityId) {
        return list(
                "active = true and category = ?1 and availableToday = true and city.id = ?2 ORDER BY name ASC",
                category,
                cityId
        );
    }

    public List<User> findByCategoryAndCity(int category, Long cityId) {
        return list("active = true and category = ?1 and city.id = ?2", category, cityId);
    }
}
