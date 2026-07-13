package br.com.stringtracker.repository;

import br.com.stringtracker.model.PlaySession;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PlaySessionRepository implements PanacheRepository<PlaySession> {
}
