package br.com.stringtracker.repository;

import br.com.stringtracker.model.AvailabilitySlot;
import br.com.stringtracker.model.User;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class AvailabilitySlotRepository implements PanacheRepository<AvailabilitySlot> {

    public List<AvailabilitySlot> findByUser(User user) {
        return list("active = true and user = ?1", user);
    }

    public void deleteByUser(User user) {
        delete("user", user);
    }
}
