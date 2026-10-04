package br.com.stringtracker.repository;

import br.com.stringtracker.model.Invite;
import br.com.stringtracker.model.InviteKind;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class InviteRepository implements PanacheRepository<Invite> {

    // O filtro `active` é explícito: @SQLRestriction na BaseEntity (@MappedSuperclass) não é aplicado pelo Hibernate.
    public Optional<Invite> findByTokenHash(String tokenHash) {
        return find("tokenHash = ?1 and active = true", tokenHash).firstResultOptional();
    }

    /** Trava a linha para que dois aceites simultâneos do mesmo convite não passem juntos. */
    public Optional<Invite> findByTokenHashForUpdate(String tokenHash) {
        return find("tokenHash = ?1 and active = true", tokenHash)
                .withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    public List<Invite> findPending(long clubId, String email, InviteKind kind) {
        return list("club.id = ?1 and email = ?2 and kind = ?3 and acceptedAt is null and active = true",
                clubId, email, kind);
    }

    /** Convites do tipo ainda não aceitos, os mais recentes primeiro; reemitir invalida o anterior, então não há repetidos. */
    public List<Invite> listPendingOfClub(long clubId, InviteKind kind) {
        return list("club.id = ?1 and kind = ?2 and acceptedAt is null and active = true order by id desc",
                clubId, kind);
    }
}
