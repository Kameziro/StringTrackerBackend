package br.com.stringtracker.repository;

import br.com.stringtracker.model.User;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class UserRepository implements PanacheRepository<User> {

    public Optional<User> findByKeycloakId(String keycloakId) {
        return find("keycloakId", keycloakId).firstResultOptional();
    }

    public Optional<User> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }

    public Optional<User> findActiveByEmailIgnoreCase(String email) {
        return find("active = true and lower(email) = lower(?1)", email.trim()).firstResultOptional();
    }

    /** Usuários ativos cujo nome contém o texto (sem diferenciar maiúsculas), por nome. */
    public List<User> searchActiveByName(String text, int limit) {
        String escaped = text.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return find("active = true and lower(name) like ?1 escape '\\' order by name, id", "%" + escaped + "%")
                .page(0, limit).list();
    }

    public List<User> findAvailableTodayByCategoryAndCity(int category, Long cityId) {
        return list(
                "category = ?1 and availableToday = true and city.id = ?2 ORDER BY name ASC",
                category,
                cityId
        );
    }

    public List<User> findByCategoryAndCity(int category, Long cityId) {
        return list("category = ?1 and city.id = ?2", category, cityId);
    }
}
