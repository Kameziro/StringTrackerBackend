package br.com.stringtracker.resource;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.GameInterest;
import br.com.stringtracker.model.GameInterestStatus;
import br.com.stringtracker.model.OpenGame;
import br.com.stringtracker.model.OpenGameStatus;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.CityRepository;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.repository.GameInterestRepository;
import br.com.stringtracker.repository.OpenGameRepository;
import br.com.stringtracker.repository.UserRepository;
import br.com.stringtracker.service.ExpoPushService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@QuarkusTest
class OpenGameCancelAndLeaveResourceTest {

    private static final String ORGANIZER = "kc-cancel-org";
    private static final String INTERESTED = "kc-cancel-interested";
    private static final String CONFIRMED = "kc-cancel-confirmed";
    private static final String DECLINED = "kc-cancel-declined";
    private static final String OUTSIDER = "kc-cancel-outsider";
    private static final long CITY_ID = 5565L;

    @Inject
    UserRepository userRepository;

    @Inject
    CityRepository cityRepository;

    @Inject
    ClubRepository clubRepository;

    @Inject
    OpenGameRepository openGameRepository;

    @Inject
    GameInterestRepository gameInterestRepository;

    private ExpoPushService push;

    @BeforeEach
    void mockPush() {
        push = Mockito.mock(ExpoPushService.class);
        Mockito.when(push.gameData(anyLong())).thenReturn(Map.of("gameId", "1"));
        QuarkusMock.installMockForType(push, ExpoPushService.class);
    }

    private User user(String keycloakId, String name) {
        return userRepository.findByKeycloakId(keycloakId).orElseGet(() -> {
            User user = new User();
            user.setKeycloakId(keycloakId);
            user.setName(name);
            user.setEmail(keycloakId + "@example.com");
            user.setCategory(5);
            user.setCity(cityRepository.findById(CITY_ID));
            userRepository.persist(user);
            return user;
        });
    }

    /** Jogo da 5ª categoria do ORGANIZER com um interessado, um confirmado e um que recusou. */
    private long game(Instant startsAt, OpenGameStatus status) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Club club = Club.create("Quadra Cancelamento " + UUID.randomUUID());
            clubRepository.persist(club);
            OpenGame game = OpenGame.create(user(ORGANIZER, "Org Cancela"), club, startsAt,
                    startsAt.plus(Duration.ofHours(2)), 5, 4, null);
            game.setStatus(status);
            openGameRepository.persist(game);
            gameInterestRepository.persist(GameInterest.create(game, user(INTERESTED, "Ana Interessada")));
            GameInterest confirmed = GameInterest.create(game, user(CONFIRMED, "Bruno Confirmado"));
            confirmed.setStatus(GameInterestStatus.CONFIRMED);
            gameInterestRepository.persist(confirmed);
            GameInterest declined = GameInterest.create(game, user(DECLINED, "Caio Recusou"));
            declined.setStatus(GameInterestStatus.DECLINED);
            gameInterestRepository.persist(declined);
            user(OUTSIDER, "Davi De Fora");
            return game.getId();
        });
    }

    private long futureGame() {
        return game(Instant.now().plus(Duration.ofDays(1)), OpenGameStatus.OPEN);
    }

    private long userId(String keycloakId) {
        return QuarkusTransaction.requiringNew().call(() -> userRepository.findByKeycloakId(keycloakId).orElseThrow().getId());
    }

    private OpenGameStatus statusOf(long gameId) {
        return QuarkusTransaction.requiringNew().call(() -> openGameRepository.findById(gameId).getStatus());
    }

    // Cancelar

    @Test
    @TestSecurity(user = ORGANIZER)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ORGANIZER)})
    void organizerCancels_gameBecomesCancelled_andOnlyInterestedAndConfirmedPlayersArePushed() {
        long gameId = futureGame();

        given().when().post("/api/games/%d/cancel".formatted(gameId))
                .then().statusCode(200)
                .body("status", equalTo("CANCELLED"));

        assertEquals(OpenGameStatus.CANCELLED, statusOf(gameId));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> recipients = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(push).notifyUsers(recipients.capture(), eq("Jogo cancelado"), body.capture(), any());
        assertEquals(List.of(userId(INTERESTED), userId(CONFIRMED)).stream().sorted().toList(),
                recipients.getValue().stream().sorted().toList());
        assertTrue(body.getValue().startsWith("Org Cancela cancelou o jogo de "), body.getValue());
        assertTrue(body.getValue().contains("Quadra Cancelamento"), body.getValue());
    }

    @Test
    @TestSecurity(user = OUTSIDER)
    @JwtSecurity(claims = {@Claim(key = "sub", value = OUTSIDER)})
    void cancelledGame_leavesTheOpenGamesList() {
        long open = futureGame();
        long cancelled = game(Instant.now().plus(Duration.ofDays(1)), OpenGameStatus.CANCELLED);

        given().when().get("/api/games")
                .then().statusCode(200)
                .body("id", hasItem((int) open))
                .body("id", not(hasItem((int) cancelled)));
    }

    @Test
    @TestSecurity(user = INTERESTED)
    @JwtSecurity(claims = {@Claim(key = "sub", value = INTERESTED)})
    void someoneOtherThanTheOrganizer_cannotCancel() {
        long gameId = futureGame();

        given().when().post("/api/games/%d/cancel".formatted(gameId)).then().statusCode(403);

        assertEquals(OpenGameStatus.OPEN, statusOf(gameId));
        verify(push, never()).notifyUsers(any(), anyString(), anyString(), any());
    }

    @Test
    @TestSecurity(user = ORGANIZER)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ORGANIZER)})
    void cancellingTwice_returns422() {
        long gameId = futureGame();
        given().when().post("/api/games/%d/cancel".formatted(gameId)).then().statusCode(200);

        given().when().post("/api/games/%d/cancel".formatted(gameId))
                .then().statusCode(422)
                .body(equalTo("Este jogo já foi cancelado"));
    }

    @Test
    @TestSecurity(user = ORGANIZER)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ORGANIZER)})
    void gameThatAlreadyStarted_cannotBeCancelled() {
        long gameId = game(Instant.now().minus(Duration.ofMinutes(30)), OpenGameStatus.OPEN);

        given().when().post("/api/games/%d/cancel".formatted(gameId))
                .then().statusCode(422)
                .body(equalTo("O jogo já começou"));
        assertEquals(OpenGameStatus.OPEN, statusOf(gameId));
    }

    @Test
    @TestSecurity(user = ORGANIZER)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ORGANIZER)})
    void unknownGame_returns404() {
        given().when().post("/api/games/999999999/cancel").then().statusCode(404);
    }

    // Desistir

    @Test
    @TestSecurity(user = INTERESTED)
    @JwtSecurity(claims = {@Claim(key = "sub", value = INTERESTED)})
    void interestedPlayerLeaves_leavesTheInterestedList_andTheOrganizerIsPushed() {
        long gameId = futureGame();

        given().when().post("/api/games/%d/decline".formatted(gameId))
                .then().statusCode(200)
                .body("interests.userId", not(hasItem((int) userId(INTERESTED))))
                .body("interests.userId", hasItem((int) userId(CONFIRMED)))
                .body("interestedCount", equalTo(1));

        verify(push).notifyUser(eq(userId(ORGANIZER)), eq("Saiu do seu jogo"), eq("Ana Interessada não vai mais jogar"), any());
    }

    @Test
    @TestSecurity(user = CONFIRMED)
    @JwtSecurity(claims = {@Claim(key = "sub", value = CONFIRMED)})
    void confirmedPlayerLeavesAConfirmedGame_reopensTheGame() {
        long gameId = game(Instant.now().plus(Duration.ofDays(1)), OpenGameStatus.CONFIRMED);

        given().when().post("/api/games/%d/decline".formatted(gameId))
                .then().statusCode(200)
                .body("status", equalTo("OPEN"));

        assertEquals(OpenGameStatus.OPEN, statusOf(gameId));
        verify(push).notifyUser(eq(userId(ORGANIZER)), eq("Saiu do seu jogo"), eq("Bruno Confirmado não vai mais jogar"), any());
    }

    @Test
    @TestSecurity(user = OUTSIDER)
    @JwtSecurity(claims = {@Claim(key = "sub", value = OUTSIDER)})
    void whoDeclinedNeverShowsInTheInterestedList() {
        long gameId = futureGame();

        given().when().get("/api/games/%d".formatted(gameId))
                .then().statusCode(200)
                .body("interests.userId", not(hasItem((int) userId(DECLINED))))
                .body("interests.status", not(hasItem("DECLINED")))
                .body("interests.size()", equalTo(2));
    }

    @Test
    @TestSecurity(user = OUTSIDER)
    @JwtSecurity(claims = {@Claim(key = "sub", value = OUTSIDER)})
    void decliningAGameNeverJoined_doesNotPushTheOrganizer() {
        long gameId = futureGame();

        given().when().post("/api/games/%d/decline".formatted(gameId)).then().statusCode(200);

        verify(push, never()).notifyUser(anyLong(), anyString(), anyString(), any());
    }

    @Test
    @TestSecurity(user = ORGANIZER)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ORGANIZER)})
    void organizerCannotLeaveTheirOwnGame() {
        long gameId = futureGame();

        given().when().post("/api/games/%d/decline".formatted(gameId))
                .then().statusCode(422)
                .body(containsString("cancele o jogo"));
    }

    @Test
    @TestSecurity(user = INTERESTED)
    @JwtSecurity(claims = {@Claim(key = "sub", value = INTERESTED)})
    void leavingACancelledOrStartedGame_returns422() {
        long cancelled = game(Instant.now().plus(Duration.ofDays(1)), OpenGameStatus.CANCELLED);
        long started = game(Instant.now().minus(Duration.ofMinutes(30)), OpenGameStatus.OPEN);

        given().when().post("/api/games/%d/decline".formatted(cancelled))
                .then().statusCode(422).body(equalTo("Este jogo foi cancelado"));
        given().when().post("/api/games/%d/decline".formatted(started))
                .then().statusCode(422).body(equalTo("O jogo já começou"));
    }
}
