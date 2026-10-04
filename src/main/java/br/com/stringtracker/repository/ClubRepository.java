package br.com.stringtracker.repository;

import br.com.stringtracker.model.Club;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ClubRepository implements PanacheRepository<Club> {

    public Optional<Club> findActiveById(Long id) {
        return find("active = true and id = ?1", id).firstResultOptional();
    }

    public List<Club> listAllActive() {
        return list("active = true ORDER BY name ASC");
    }

    public Optional<Club> findByNameIgnoreCase(String name) {
        return find("active = true and lower(name) = lower(?1)", name.trim()).firstResultOptional();
    }

    /** Cria o clube/lugar se ainda não existir (nome livre do usuário). */
    public Club findOrCreateByName(String name) {
        String trimmed = name.trim();
        return findByNameIgnoreCase(trimmed).orElseGet(() -> {
            Club club = Club.create(trimmed);
            persist(club);
            return club;
        });
    }
}
