package br.com.stringtracker.repository;

import br.com.stringtracker.model.ClubPhoto;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ClubPhotoRepository implements PanacheRepository<ClubPhoto> {

    // O filtro `active` é explícito: @SQLRestriction na BaseEntity (@MappedSuperclass) não é aplicado pelo Hibernate.
    public List<ClubPhoto> listByClub(long clubId) {
        return list("club.id = ?1 and active = true order by position, id", clubId);
    }

    public Optional<ClubPhoto> findByIdAndClub(long photoId, long clubId) {
        return find("id = ?1 and club.id = ?2 and active = true", photoId, clubId).firstResultOptional();
    }
}
