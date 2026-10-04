package br.com.stringtracker.repository;

import br.com.stringtracker.model.ClubCoach;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class ClubCoachRepository implements PanacheRepository<ClubCoach> {

    /** Vínculo ativo ou não: a restrição única (club_id, coach_id) vale para os dois. */
    public Optional<ClubCoach> findByClubAndCoach(long clubId, long coachId) {
        return find("club.id = ?1 and coach.id = ?2", clubId, coachId).firstResultOptional();
    }
}
