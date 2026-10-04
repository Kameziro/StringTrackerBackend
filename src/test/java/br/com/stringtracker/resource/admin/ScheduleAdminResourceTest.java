package br.com.stringtracker.resource.admin;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubAdmin;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.User;
import br.com.stringtracker.model.schedule.LessonKind;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonSlotStatus;
import br.com.stringtracker.repository.ClubAdminRepository;
import br.com.stringtracker.repository.ClubCoachRepository;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.repository.CoachRepository;
import br.com.stringtracker.repository.LessonSlotRepository;
import br.com.stringtracker.repository.UserRepository;
import br.com.stringtracker.service.ClockProducer;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ScheduleAdminResourceTest {

    private static final String ADMIN = "kc-sched-admin";
    private static final String OTHER_ADMIN = "kc-sched-other-admin";
    private static final String COMMON = "kc-sched-common";

    /** Segunda-feira 2026-10-05, 09:00 em São Paulo. A janela de 28 dias vai até 2026-11-01. */
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final String OTHER_CLUB_NAME = "Clube Secreto";

    @Inject
    UserRepository userRepository;

    @Inject
    ClubRepository clubRepository;

    @Inject
    ClubAdminRepository clubAdminRepository;

    @Inject
    CoachRepository coachRepository;

    @Inject
    ClubCoachRepository clubCoachRepository;

    @Inject
    LessonSlotRepository lessonSlotRepository;

    private long clubAId;
    private long clubBId;
    private long coachId;

    /** O professor atende nos clubes A e B, com preço de singles e duplas; ADMIN administra os dois clubes. */
    @BeforeEach
    void seed() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
        QuarkusTransaction.requiringNew().run(() -> {
            User admin = user(ADMIN);
            User otherAdmin = user(OTHER_ADMIN);
            user(COMMON);
            Club clubA = club("Agenda Clube A");
            Club clubB = club(OTHER_CLUB_NAME);
            Club clubC = club("Agenda Clube C");
            clubAdminRepository.persist(ClubAdmin.create(clubA, admin));
            clubAdminRepository.persist(ClubAdmin.create(clubB, admin));
            clubAdminRepository.persist(ClubAdmin.create(clubC, otherAdmin));
            Coach coach = coach(user("kc-sched-coach-" + UUID.randomUUID()));
            link(clubA, coach, 10000L, 15000L);
            link(clubB, coach, 10000L, 15000L);
            clubAId = clubA.getId();
            clubBId = clubB.getId();
            coachId = coach.getId();
        });
    }

    private Club club(String prefix) {
        Club club = Club.create(prefix + " " + UUID.randomUUID());
        clubRepository.persist(club);
        return club;
    }

    private User user(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId).orElseGet(() -> {
            User user = new User();
            user.setKeycloakId(keycloakId);
            user.setName(keycloakId);
            user.setEmail(keycloakId + "@example.com");
            userRepository.persist(user);
            return user;
        });
    }

    private Coach coach(User user) {
        Coach coach = Coach.create(user);
        coach.setOffersSingles(true);
        coach.setOffersDoubles(true);
        coach.setOffersGroup(false);
        coachRepository.persist(coach);
        return coach;
    }

    private ClubCoach link(Club club, Coach coach, Long singlesCents, Long doublesCents) {
        ClubCoach link = ClubCoach.create(club, coach);
        link.setPriceSinglesCents(singlesCents);
        link.setPriceDoublesCents(doublesCents);
        clubCoachRepository.persist(link);
        return link;
    }

    private static String blocksUrl(long clubId, long coachId) {
        return "/api/admin/clubs/%d/coaches/%d/blocks".formatted(clubId, coachId);
    }

    private static String block(int dayOfWeek, String start, String end, int minutes) {
        return "{\"dayOfWeek\":%d,\"startTime\":\"%s\",\"endTime\":\"%s\",\"durationMinutes\":%d}"
                .formatted(dayOfWeek, start, end, minutes);
    }

    private List<LessonSlot> slotsOf(long coach) {
        return lessonSlotRepository.list("coach.id = ?1 order by startsAt", coach);
    }

    private static Instant saoPaulo(LocalDate day, int hour, int minute) {
        return ZonedDateTime.of(day, LocalTime.of(hour, minute), ClockProducer.ZONE).toInstant();
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void tuesday18to21With60Minutes_createsThreeSlotsPerTuesdayForFourWeeks() {
        given().contentType(ContentType.JSON).body(block(2, "18:00", "21:00", 60))
                .when().post(blocksUrl(clubAId, coachId))
                .then().statusCode(201)
                .body("coachId", equalTo((int) coachId))
                .body("dayOfWeek", equalTo(2))
                .body("durationMinutes", equalTo(60))
                .body("slotsCreated", equalTo(12));

        List<LessonSlot> slots = slotsOf(coachId);
        assertEquals(12, slots.size());
        List<Instant> expected = new ArrayList<>();
        for (String day : List.of("2026-10-06", "2026-10-13", "2026-10-20", "2026-10-27")) {
            for (int hour : new int[]{18, 19, 20}) {
                expected.add(saoPaulo(LocalDate.parse(day), hour, 0));
            }
        }
        assertEquals(expected, slots.stream().map(LessonSlot::getStartsAt).toList());
        for (LessonSlot slot : slots) {
            assertEquals(3600, slot.getEndsAt().getEpochSecond() - slot.getStartsAt().getEpochSecond());
            assertEquals(LessonKind.PRIVATE, slot.getKind());
            assertEquals(LessonSlotStatus.OPEN, slot.getStatus());
            assertEquals(1, slot.getCapacity());
        }
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void ninetyMinuteLessons_fitTwiceInThreeHours() {
        given().contentType(ContentType.JSON).body(block(3, "18:00", "21:00", 90))
                .when().post(blocksUrl(clubAId, coachId))
                .then().statusCode(201)
                .body("slotsCreated", equalTo(8));

        List<LessonSlot> firstDay = slotsOf(coachId).subList(0, 2);
        assertEquals(saoPaulo(LocalDate.parse("2026-10-07"), 18, 0), firstDay.get(0).getStartsAt());
        assertEquals(saoPaulo(LocalDate.parse("2026-10-07"), 19, 30), firstDay.get(1).getStartsAt());
        assertEquals(saoPaulo(LocalDate.parse("2026-10-07"), 21, 0), firstDay.get(1).getEndsAt());
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void slotsThatAlreadyStartedTodayAreNotCreated() {
        // Hoje é segunda 09:00: os horários das 08h e das 09h já começaram, o das 10h não.
        given().contentType(ContentType.JSON).body(block(1, "08:00", "11:00", 60))
                .when().post(blocksUrl(clubAId, coachId))
                .then().statusCode(201)
                .body("slotsCreated", equalTo(10));

        assertEquals(saoPaulo(LocalDate.parse("2026-10-05"), 10, 0), slotsOf(coachId).get(0).getStartsAt());
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void overlapWithTheSameCoachAtAnotherClub_returns409WithoutLeakingTheOtherClub_andCreatesNothing() {
        given().contentType(ContentType.JSON).body(block(2, "18:00", "21:00", 60))
                .when().post(blocksUrl(clubAId, coachId))
                .then().statusCode(201);

        // 17h-19h: a aula das 17h está livre, a das 18h conflita; a transação inteira deve ser desfeita.
        given().contentType(ContentType.JSON).body(block(2, "17:00", "19:00", 60))
                .when().post(blocksUrl(clubBId, coachId))
                .then().statusCode(409)
                .body(equalTo("O professor já tem compromisso nesse horário"))
                .body(not(containsString(OTHER_CLUB_NAME)));

        assertEquals(12, slotsOf(coachId).size());
        assertEquals(0, lessonSlotRepository.count("clubCoach.club.id", clubBId));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void adjacentBlocks_doNotConflict() {
        given().contentType(ContentType.JSON).body(block(2, "18:00", "21:00", 60))
                .when().post(blocksUrl(clubAId, coachId)).then().statusCode(201);

        given().contentType(ContentType.JSON).body(block(2, "21:00", "22:00", 60))
                .when().post(blocksUrl(clubBId, coachId))
                .then().statusCode(201)
                .body("slotsCreated", equalTo(4));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void twoClubsCreatingTheSameHoursAtTheSameTime_oneWinsAndTheOtherGets409() throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (long clubId : new long[]{clubAId, clubBId}) {
                results.add(pool.submit(() -> {
                    go.await();
                    return given().contentType(ContentType.JSON).body(block(4, "18:00", "20:00", 60))
                            .when().post(blocksUrl(clubId, coachId))
                            .then().extract().statusCode();
                }));
            }
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertTrue(statuses.contains(201) && statuses.contains(409), "statuses: " + statuses);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(8, slotsOf(coachId).size());
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void coachWithoutAPriceForAnyOfferedType_returns422() {
        long bareCoachId = QuarkusTransaction.requiringNew().call(() -> {
            Coach coach = coach(user("kc-sched-bare-" + UUID.randomUUID()));
            // Só tem preço de grupo, que ele não oferece, e nenhum de singles ou duplas.
            ClubCoach link = link(clubRepository.findById(clubAId), coach, null, null);
            link.setPriceGroupCents(8000L);
            return coach.getId();
        });

        given().contentType(ContentType.JSON).body(block(2, "18:00", "21:00", 60))
                .when().post(blocksUrl(clubAId, bareCoachId))
                .then().statusCode(422)
                .body(equalTo("Defina os preços do professor antes de abrir a agenda"));

        assertEquals(0, slotsOf(bareCoachId).size());
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void priceOnlyForATypeTheCoachDoesNotOffer_returns422() {
        QuarkusTransaction.requiringNew().run(() -> coachRepository.findById(coachId).setOffersDoubles(false));
        QuarkusTransaction.requiringNew().run(() -> {
            ClubCoach link = clubCoachRepository.findByClubAndCoach(clubAId, coachId).orElseThrow();
            link.setPriceSinglesCents(null);
        });

        given().contentType(ContentType.JSON).body(block(2, "18:00", "21:00", 60))
                .when().post(blocksUrl(clubAId, coachId))
                .then().statusCode(422);
    }

    @Test
    @TestSecurity(user = OTHER_ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = OTHER_ADMIN)})
    void adminOfAnotherClub_returns403() {
        given().contentType(ContentType.JSON).body(block(2, "18:00", "21:00", 60))
                .when().post(blocksUrl(clubAId, coachId))
                .then().statusCode(403);

        assertEquals(0, slotsOf(coachId).size());
    }

    @Test
    @TestSecurity(user = COMMON)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COMMON)})
    void commonUser_returns403() {
        given().contentType(ContentType.JSON).body(block(2, "18:00", "21:00", 60))
                .when().post(blocksUrl(clubAId, coachId))
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void coachNotLinkedToTheClub_orUnlinked_returns404() {
        long strangerId = QuarkusTransaction.requiringNew().call(() -> coach(user("kc-sched-stranger-" + UUID.randomUUID())).getId());
        QuarkusTransaction.requiringNew().run(() ->
                clubCoachRepository.findByClubAndCoach(clubBId, coachId).orElseThrow().markExcluded());

        given().contentType(ContentType.JSON).body(block(2, "18:00", "21:00", 60))
                .when().post(blocksUrl(clubAId, strangerId))
                .then().statusCode(404);
        given().contentType(ContentType.JSON).body(block(2, "18:00", "21:00", 60))
                .when().post(blocksUrl(clubBId, coachId))
                .then().statusCode(404);
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void invalidBlocks_return400AndCreateNothing() {
        for (String body : new String[]{
                block(2, "18:00", "21:00", 45),
                block(2, "18:00", "20:30", 60),
                block(2, "21:00", "18:00", 60),
                block(2, "18:00", "18:00", 60),
                block(8, "18:00", "21:00", 60),
                block(0, "18:00", "21:00", 60),
                "{\"dayOfWeek\":2,\"endTime\":\"21:00\",\"durationMinutes\":60}"
        }) {
            given().contentType(ContentType.JSON).body(body)
                    .when().post(blocksUrl(clubAId, coachId))
                    .then().statusCode(400);
        }

        assertEquals(0, slotsOf(coachId).size());
    }
}
