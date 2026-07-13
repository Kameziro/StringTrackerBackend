package br.com.stringtracker.service;

import br.com.stringtracker.dto.CreatePlaySessionRequest;
import br.com.stringtracker.dto.PlaySessionResponse;
import br.com.stringtracker.model.PlaySession;
import br.com.stringtracker.model.Racket;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.PlaySessionRepository;
import br.com.stringtracker.repository.RacketRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class PlaySessionService {

    @Inject
    RacketRepository racketRepository;

    @Inject
    PlaySessionRepository playSessionRepository;

    @Transactional
    public PlaySessionResponse create(User user, CreatePlaySessionRequest request) {
        Racket racket = racketRepository.findOwnedBy(user, request.racketId())
                .orElseThrow(() -> new NotFoundException("Raquete não encontrada."));

        PlaySession session = PlaySession.create(racket, request.durationMinutes(), request.datePlayed());
        playSessionRepository.persist(session);
        racket.addPlayMinutes(request.durationMinutes());

        return PlaySessionResponse.from(session, racket);
    }
}
