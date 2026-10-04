package br.com.stringtracker.repository;

import br.com.stringtracker.model.DeviceToken;
import br.com.stringtracker.model.User;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class DeviceTokenRepository implements PanacheRepository<DeviceToken> {

    public Optional<DeviceToken> findByToken(String expoPushToken) {
        return find("active = true and expoPushToken = ?1", expoPushToken).firstResultOptional();
    }

    public List<DeviceToken> findByUser(User user) {
        return list("active = true and user = ?1", user);
    }

    public List<DeviceToken> findByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return list("active = true and user.id in ?1", userIds);
    }
}
