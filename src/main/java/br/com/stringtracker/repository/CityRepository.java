package br.com.stringtracker.repository;

import br.com.stringtracker.model.City;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class CityRepository implements PanacheRepository<City> {

    public List<City> listByStateId(Long stateId) {
        return list("state.id = ?1 ORDER BY name ASC", stateId);
    }
}
