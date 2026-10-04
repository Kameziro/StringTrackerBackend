package br.com.stringtracker.repository;

import br.com.stringtracker.model.ClubAdmin;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class ClubAdminRepository implements PanacheRepository<ClubAdmin> {

    // O filtro `active` é explícito: o Hibernate não aplica filtros herdados da BaseEntity (@MappedSuperclass).
    public boolean isAdmin(long clubId, long userId) {
        return count("club.id = ?1 and user.id = ?2 and active = true", clubId, userId) > 0;
    }

    /** Vínculo do usuário com o clube, ativo ou não: a restrição única (club_id, user_id) vale para os dois. */
    public Optional<ClubAdmin> findLink(long clubId, long userId) {
        return find("club.id = ?1 and user.id = ?2", clubId, userId).firstResultOptional();
    }

    public Set<Long> findClubIdsByUserId(long userId) {
        return list("user.id = ?1 and active = true", userId).stream()
                .map(admin -> admin.getClub().getId())
                .collect(Collectors.toSet());
    }
}
