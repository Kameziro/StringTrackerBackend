package br.com.stringtracker.service;

import br.com.stringtracker.client.ExpoPushClient;
import br.com.stringtracker.client.ExpoPushClient.ExpoPushMessage;
import br.com.stringtracker.model.DeviceToken;
import br.com.stringtracker.repository.DeviceTokenRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ExpoPushService {

    private static final Logger LOG = Logger.getLogger(ExpoPushService.class);

    @Inject
    DeviceTokenRepository deviceTokenRepository;

    @Inject
    @RestClient
    ExpoPushClient expoPushClient;

    public void notifyUsers(
            Collection<Long> userIds,
            String title,
            String body,
            Map<String, Object> data
    ) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<DeviceToken> tokens = deviceTokenRepository.findByUserIds(userIds);
        if (tokens.isEmpty()) {
            LOG.debugf("No device tokens for users %s", userIds);
            return;
        }
        Map<String, Object> payload = data != null ? data : Map.of();
        List<ExpoPushMessage> messages = new ArrayList<>();
        for (DeviceToken token : tokens) {
            messages.add(ExpoPushMessage.of(token.getExpoPushToken(), title, body, payload));
        }
        try {
            expoPushClient.send(messages);
        } catch (Exception ex) {
            LOG.warnf(ex, "Failed to send Expo push to %d devices", messages.size());
        }
    }

    public void notifyUser(Long userId, String title, String body, Map<String, Object> data) {
        notifyUsers(List.of(userId), title, body, data);
    }

    public Map<String, Object> gameData(Long gameId) {
        Map<String, Object> data = new HashMap<>();
        data.put("gameId", String.valueOf(gameId));
        data.put("type", "open_game");
        return data;
    }
}
