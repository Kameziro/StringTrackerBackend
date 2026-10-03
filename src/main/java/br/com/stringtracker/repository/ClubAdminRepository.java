package br.com.stringtracker.repository;

import br.com.stringtracker.model.ClubAdmin;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ClubAdminRepository implements PanacheRepository<ClubAdmin> {
}
