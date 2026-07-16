package br.com.stringtracker.repository;

import br.com.stringtracker.model.State;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class StateRepository implements PanacheRepository<State> {

    public List<State> listAllOrdered() {
        return list("ORDER BY name ASC");
    }
}
