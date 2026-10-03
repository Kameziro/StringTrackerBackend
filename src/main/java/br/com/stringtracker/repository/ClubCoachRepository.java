package br.com.stringtracker.repository;

import br.com.stringtracker.model.ClubCoach;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ClubCoachRepository implements PanacheRepository<ClubCoach> {
}
