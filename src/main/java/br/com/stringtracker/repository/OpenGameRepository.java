package br.com.stringtracker.repository;

import br.com.stringtracker.model.OpenGame;
import br.com.stringtracker.model.OpenGameStatus;
import br.com.stringtracker.model.User;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class OpenGameRepository implements PanacheRepository<OpenGame> {

    /**
     * Jogos abertos visíveis: mesma categoria e cidade (sem grupo),
     * ou jogos de grupos dos quais faço parte.
     */
    public List<OpenGame> findOpenVisibleTo(User user, int category, Long cityId) {
        return list(
                """
                status = ?1 and (
                  (group is null and category = ?2 and organizer.city.id = ?3)
                  or (group is not null and group.id in (
                    select gm.group.id from GroupMember gm where gm.user = ?4
                  ))
                )
                ORDER BY startsAt ASC
                """,
                OpenGameStatus.OPEN,
                category,
                cityId,
                user
        );
    }

    public List<OpenGame> findForUser(User user) {
        List<OpenGame> organized = list("organizer", user);
        List<OpenGame> interested = list(
                "id IN (SELECT gi.game.id FROM GameInterest gi WHERE gi.user = ?1)",
                user
        );
        Map<Long, OpenGame> byId = new LinkedHashMap<>();
        for (OpenGame game : organized) {
            byId.put(game.getId(), game);
        }
        for (OpenGame game : interested) {
            byId.putIfAbsent(game.getId(), game);
        }
        List<OpenGame> merged = new ArrayList<>(byId.values());
        merged.sort(Comparator.comparing(OpenGame::getStartsAt).reversed());
        return merged;
    }
}
