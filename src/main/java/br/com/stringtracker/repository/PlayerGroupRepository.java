package br.com.stringtracker.repository;

import br.com.stringtracker.model.PlayerGroup;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PlayerGroupRepository implements PanacheRepository<PlayerGroup> {

    public Optional<PlayerGroup> findActiveById(Long id) {
        return find("active = true and id = ?1", id).firstResultOptional();
    }

    public List<PlayerGroup> listAllOrdered() {
        return list("active = true ORDER BY name ASC");
    }

    public Optional<PlayerGroup> findByNameIgnoreCase(String name) {
        return find("active = true and lower(name) = ?1", name.toLowerCase()).firstResultOptional();
    }
}
