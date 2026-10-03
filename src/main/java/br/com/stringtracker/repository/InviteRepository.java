package br.com.stringtracker.repository;

import br.com.stringtracker.model.Invite;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InviteRepository implements PanacheRepository<Invite> {
}
