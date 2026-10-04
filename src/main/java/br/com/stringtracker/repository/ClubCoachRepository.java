package br.com.stringtracker.repository;

import br.com.stringtracker.model.ClubCoach;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ClubCoachRepository implements PanacheRepository<ClubCoach> {

    /** Professores vinculados ao clube, por nome. */
    public List<ClubCoach> listActiveOfClub(long clubId) {
        return list("""
                select l from ClubCoach l join fetch l.coach c join fetch c.user u
                where l.club.id = ?1 and l.active = true order by u.name, l.id
                """, clubId);
    }

    /** Vínculos ativos do professor com clubes ativos, por nome do clube. */
    public List<ClubCoach> listActiveOfCoach(long coachId) {
        return list("""
                select l from ClubCoach l join fetch l.club c join fetch l.coach co join fetch co.user
                where co.id = ?1 and l.active = true and c.active = true order by c.name, c.id
                """, coachId);
    }

    /** Vínculo ativo ou não: a restrição única (club_id, coach_id) vale para os dois. */
    public Optional<ClubCoach> findByClubAndCoach(long clubId, long coachId) {
        return find("club.id = ?1 and coach.id = ?2", clubId, coachId).firstResultOptional();
    }
}
