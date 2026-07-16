package br.com.stringtracker.service;

import br.com.stringtracker.dto.CreateOpenGameRequest;
import br.com.stringtracker.dto.OpenGameResponse;
import br.com.stringtracker.dto.PlayerResponse;
import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.GameInterest;
import br.com.stringtracker.model.GameInterestStatus;
import br.com.stringtracker.model.OpenGame;
import br.com.stringtracker.model.OpenGameStatus;
import br.com.stringtracker.model.PlayerGroup;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.repository.GameInterestRepository;
import br.com.stringtracker.repository.OpenGameRepository;
import br.com.stringtracker.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@ApplicationScoped
public class OpenGameService {

    @Inject
    OpenGameRepository openGameRepository;

    @Inject
    ClubRepository clubRepository;

    @Inject
    GameInterestRepository gameInterestRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    GroupService groupService;

    @Inject
    ExpoPushService expoPushService;

    @Transactional
    public OpenGameResponse create(User organizer, CreateOpenGameRequest request) {
        if (organizer.getCategory() == null) {
            throw new BadRequestException("Complete seu perfil com a categoria antes de criar um jogo");
        }
        if (organizer.getCity() == null) {
            throw new BadRequestException("Informe sua cidade no perfil antes de criar um jogo");
        }
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BadRequestException("endsAt must be after startsAt");
        }
        String place = request.place().trim();
        if (place.isEmpty()) {
            throw new BadRequestException("Informe o lugar do jogo");
        }
        Club club = clubRepository.findOrCreateByName(place);
        int capacity = request.capacity() != null ? request.capacity() : 4;
        int category = request.category();

        PlayerGroup group = null;
        if (request.groupId() != null) {
            group = groupService.requireGroup(request.groupId());
            if (!groupService.isMember(group, organizer)) {
                throw new ForbiddenException("Entre no grupo antes de publicar um jogo nele");
            }
        }

        OpenGame game = OpenGame.create(
                organizer,
                club,
                request.startsAt(),
                request.endsAt(),
                category,
                capacity,
                group
        );
        openGameRepository.persist(game);

        List<Long> notifyIds;
        String pushBody;
        if (group != null) {
            notifyIds = groupService.memberUserIds(group).stream()
                    .filter(id -> !Objects.equals(id, organizer.getId()))
                    .toList();
            pushBody = String.format("%s · %s · grupo %s", club.getName(), organizer.getName(), group.getName());
        } else {
            notifyIds = userRepository.findByCategoryAndCity(category, organizer.getCity().getId()).stream()
                    .map(User::getId)
                    .filter(id -> !Objects.equals(id, organizer.getId()))
                    .toList();
            pushBody = String.format("%s · %s · %dª categoria", club.getName(), organizer.getName(), category);
        }
        expoPushService.notifyUsers(
                notifyIds,
                "Jogo aberto",
                pushBody,
                expoPushService.gameData(game.getId())
        );

        return toResponse(game);
    }

    public List<OpenGameResponse> listOpenVisibleTo(User user) {
        if (user.getCategory() == null) {
            throw new BadRequestException("Complete seu perfil com a categoria");
        }
        if (user.getCity() == null) {
            throw new BadRequestException("Informe sua cidade no perfil");
        }
        return openGameRepository.findOpenVisibleTo(user, user.getCategory(), user.getCity().getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<OpenGameResponse> listMine(User user) {
        return openGameRepository.findForUser(user).stream()
                .map(this::toResponse)
                .toList();
    }

    public OpenGameResponse get(Long id) {
        return toResponse(requireGame(id));
    }

    @Transactional
    public OpenGameResponse expressInterest(User user, Long gameId) {
        OpenGame game = requireGame(gameId);
        if (game.getStatus() != OpenGameStatus.OPEN && game.getStatus() != OpenGameStatus.FULL) {
            throw new BadRequestException("Este jogo não está aberto para interesse");
        }
        if (Objects.equals(game.getOrganizer().getId(), user.getId())) {
            throw new BadRequestException("Organizador já está no jogo");
        }
        if (game.isGroupScoped()) {
            if (!groupService.isMember(game.getGroup(), user)) {
                throw new ForbiddenException("Só membros do grupo podem aceitar este jogo");
            }
        } else {
            if (user.getCategory() == null || user.getCategory() != game.getCategory()) {
                throw new BadRequestException("Categoria incompatível com o jogo");
            }
            if (user.getCity() == null
                    || game.getOrganizer().getCity() == null
                    || !Objects.equals(user.getCity().getId(), game.getOrganizer().getCity().getId())) {
                throw new BadRequestException("Só jogadores da mesma cidade podem aceitar este jogo");
            }
        }
        GameInterest existing = gameInterestRepository.findByGameAndUser(game, user).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == GameInterestStatus.DECLINED) {
                existing.setStatus(GameInterestStatus.INTERESTED);
            }
        } else {
            if (gameInterestRepository.countInterested(game) >= game.seatsNeeded()
                    && game.getStatus() == OpenGameStatus.FULL) {
                throw new BadRequestException("Jogo já está completo");
            }
            gameInterestRepository.persist(GameInterest.create(game, user));
        }

        refreshFullStatus(game);

        expoPushService.notifyUser(
                game.getOrganizer().getId(),
                "Interesse no seu jogo",
                user.getName() + " quer jogar",
                expoPushService.gameData(game.getId())
        );

        return toResponse(game);
    }

    @Transactional
    public OpenGameResponse decline(User user, Long gameId) {
        OpenGame game = requireGame(gameId);
        GameInterest interest = gameInterestRepository.findByGameAndUser(game, user)
                .orElseGet(() -> {
                    GameInterest created = GameInterest.create(game, user);
                    created.setStatus(GameInterestStatus.DECLINED);
                    gameInterestRepository.persist(created);
                    return created;
                });
        interest.setStatus(GameInterestStatus.DECLINED);
        if (game.getStatus() == OpenGameStatus.FULL) {
            game.setStatus(OpenGameStatus.OPEN);
        }
        return toResponse(game);
    }

    @Transactional
    public OpenGameResponse confirm(User organizer, Long gameId) {
        OpenGame game = requireGame(gameId);
        if (!Objects.equals(game.getOrganizer().getId(), organizer.getId())) {
            throw new ForbiddenException("Só o organizador pode confirmar");
        }
        List<GameInterest> interested = gameInterestRepository.findInterestedOrdered(game);
        int needed = game.seatsNeeded();
        if (interested.size() < needed) {
            throw new BadRequestException(
                    "Ainda faltam jogadores (" + interested.size() + "/" + needed + ")"
            );
        }
        List<GameInterest> selected = interested.subList(0, needed);
        for (GameInterest interest : selected) {
            interest.setStatus(GameInterestStatus.CONFIRMED);
        }
        for (int i = needed; i < interested.size(); i++) {
            interested.get(i).setStatus(GameInterestStatus.DECLINED);
        }
        game.setStatus(OpenGameStatus.CONFIRMED);

        List<Long> playerIds = selected.stream()
                .map(i -> i.getUser().getId())
                .collect(Collectors.toList());
        expoPushService.notifyUsers(
                playerIds,
                "Jogo confirmado",
                game.getClub().getName() + " · time fechado",
                expoPushService.gameData(game.getId())
        );

        return toResponse(game);
    }

    public List<PlayerResponse> listAvailablePlayers(int category, Long cityId, Long excludeUserId) {
        return userRepository.findAvailableTodayByCategoryAndCity(category, cityId).stream()
                .filter(u -> !Objects.equals(u.getId(), excludeUserId))
                .map(PlayerResponse::from)
                .toList();
    }

    private void refreshFullStatus(OpenGame game) {
        long interested = gameInterestRepository.countInterested(game);
        if (interested >= game.seatsNeeded()) {
            game.setStatus(OpenGameStatus.FULL);
            expoPushService.notifyUser(
                    game.getOrganizer().getId(),
                    "Time completo",
                    interested + " atletas interessados — confirme o jogo",
                    expoPushService.gameData(game.getId())
            );
        } else if (game.getStatus() == OpenGameStatus.FULL) {
            game.setStatus(OpenGameStatus.OPEN);
        }
    }

    private OpenGame requireGame(Long id) {
        return openGameRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Game not found"));
    }

    private OpenGameResponse toResponse(OpenGame game) {
        return OpenGameResponse.from(game, gameInterestRepository.findByGame(game));
    }
}
