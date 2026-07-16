package br.com.stringtracker.repository;

import br.com.stringtracker.model.GameInterest;
import br.com.stringtracker.model.GameInterestStatus;
import br.com.stringtracker.model.OpenGame;
import br.com.stringtracker.model.User;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class GameInterestRepository implements PanacheRepository<GameInterest> {

    public Optional<GameInterest> findByGameAndUser(OpenGame game, User user) {
        return find("game = ?1 and user = ?2", game, user).firstResultOptional();
    }

    public List<GameInterest> findByGame(OpenGame game) {
        return list("game", game);
    }

    public long countInterested(OpenGame game) {
        return count("game = ?1 and status = ?2", game, GameInterestStatus.INTERESTED);
    }

    public List<GameInterest> findInterestedOrdered(OpenGame game) {
        return list(
                "game = ?1 and status = ?2 ORDER BY registrationDate ASC",
                game,
                GameInterestStatus.INTERESTED
        );
    }
}
