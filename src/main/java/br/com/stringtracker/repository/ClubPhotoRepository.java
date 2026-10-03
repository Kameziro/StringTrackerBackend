package br.com.stringtracker.repository;

import br.com.stringtracker.model.ClubPhoto;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ClubPhotoRepository implements PanacheRepository<ClubPhoto> {
}
