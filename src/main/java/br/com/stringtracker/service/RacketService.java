package br.com.stringtracker.service;

import br.com.stringtracker.dto.CreateRacketRequest;
import br.com.stringtracker.dto.RacketResponse;
import br.com.stringtracker.model.Racket;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.RacketRepository;
import br.com.stringtracker.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class RacketService {

    @Inject
    RacketRepository racketRepository;

    @Inject
    UserRepository userRepository;

    public List<RacketResponse> listFor(User user) {
        return racketRepository.findByUser(user).stream()
                .map(RacketResponse::from)
                .toList();
    }

    @Transactional
    public RacketResponse create(User user, CreateRacketRequest request) {
        User locked = userRepository.lockById(user.getId());
        FreemiumPolicy.assertCanCreateRacket(locked, racketRepository.countByUser(locked));

        Racket racket = Racket.create(
                locked,
                request.brand(),
                request.model(),
                request.stringModel(),
                request.tensionLbs(),
                request.dateStrung()
        );
        racketRepository.persist(racket);
        return RacketResponse.from(racket);
    }
}
