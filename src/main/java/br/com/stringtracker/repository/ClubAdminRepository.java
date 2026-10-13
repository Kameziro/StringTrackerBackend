package br.com.stringtracker.repository;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubAdmin;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
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

    /** Admins ativos do clube, por nome. */
    public List<ClubAdmin> listActiveOfClub(long clubId) {
        return list("""
                select a from ClubAdmin a join fetch a.user u
                where a.club.id = ?1 and a.active = true order by u.name, a.id
                """, clubId);
    }

    /** Clubes ativos que o usuário administra, por nome. */
    public List<Club> listActiveClubsOfUser(long userId) {
        return list("""
                select a from ClubAdmin a join fetch a.club c
                where a.user.id = ?1 and a.active = true and c.active = true order by c.name, c.id
                """, userId).stream().map(ClubAdmin::getClub).toList();
    }

    public Set<Long> findClubIdsByUserId(long userId) {
        return list("user.id = ?1 and active = true", userId).stream()
                .map(admin -> admin.getClub().getId())
                .collect(Collectors.toSet());
    }
}
