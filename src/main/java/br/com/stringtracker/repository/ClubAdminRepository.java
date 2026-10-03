package br.com.stringtracker.repository;

import br.com.stringtracker.model.ClubAdmin;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class ClubAdminRepository implements PanacheRepository<ClubAdmin> {

    public boolean isAdmin(long clubId, long userId) {
        return count("club.id = ?1 and user.id = ?2", clubId, userId) > 0;
    }

    public Set<Long> findClubIdsByUserId(long userId) {
        return list("user.id", userId).stream()
                .map(admin -> admin.getClub().getId())
                .collect(Collectors.toSet());
    }
}
