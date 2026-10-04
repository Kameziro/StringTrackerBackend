package br.com.stringtracker.service.job;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.User;
import br.com.stringtracker.model.schedule.DayBlock;
import br.com.stringtracker.model.schedule.LessonKind;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.ScheduleBlock;
import br.com.stringtracker.repository.ClubCoachRepository;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.repository.CoachRepository;
import br.com.stringtracker.repository.DayBlockRepository;
import br.com.stringtracker.repository.LessonSlotRepository;
import br.com.stringtracker.repository.ScheduleBlockRepository;
import br.com.stringtracker.repository.UserRepository;
import br.com.stringtracker.service.ClockProducer;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SlotGenerationJobTest {

    @Inject
    SlotGenerationJob job;

    @Inject
    UserRepository userRepository;

    @Inject
    ClubRepository clubRepository;

    @Inject
    CoachRepository coachRepository;

    @Inject
    ClubCoachRepository clubCoachRepository;

    @Inject
    ScheduleBlockRepository scheduleBlockRepository;

    @Inject
    LessonSlotRepository lessonSlotRepository;

    @Inject
    DayBlockRepository dayBlockRepository;

    /** Hoje: segunda-feira 2026-10-05 às 09:00 em São Paulo, salvo quando o teste avança o relógio. */
    private static void setClock(String instant) {
        QuarkusMock.installMockForType(Clock.fixed(Instant.parse(instant), ClockProducer.ZONE), Clock.class);
    }

    private ClubCoach newClubCoach(String name) {
        Club club = Club.create(name + " " + UUID.randomUUID());
        clubRepository.persist(club);
        User user = new User();
        user.setKeycloakId("kc-slotjob-" + UUID.randomUUID());
        user.setName("Professor");
        user.setEmail(user.getKeycloakId() + "@example.com");
        userRepository.persist(user);
        Coach coach = Coach.create(user);
        coachRepository.persist(coach);
        ClubCoach link = ClubCoach.create(club, coach);
        link.setPriceSinglesCents(10000L);
        clubCoachRepository.persist(link);
        return link;
    }

    /** 18h-21h, 60 min, no dia da semana pedido (1 = segunda), sem horários gerados ainda. */
    private long newBlock(ClubCoach link, int dayOfWeek) {
        ScheduleBlock block = ScheduleBlock.create(link, LessonKind.PRIVATE, (short) dayOfWeek,
                LocalTime.of(18, 0), LocalTime.of(21, 0), (short) 60, (short) 1);
        scheduleBlockRepository.persist(block);
        return block.getId();
    }

    private record Seed(long coachId, long clubId, long blockId) {
    }

    private Seed seed(int dayOfWeek) {
        return QuarkusTransaction.requiringNew().call(() -> {
            ClubCoach link = newClubCoach("Job Clube");
            return new Seed(link.getCoach().getId(), link.getClub().getId(), newBlock(link, dayOfWeek));
        });
    }

    private List<Instant> startsOf(long coachId) {
        return lessonSlotRepository.list("coach.id = ?1 order by startsAt", coachId).stream()
                .map(LessonSlot::getStartsAt).toList();
    }

    private static Instant saoPaulo(String day, int hour) {
        return ZonedDateTime.of(LocalDate.parse(day), LocalTime.of(hour, 0), ClockProducer.ZONE).toInstant();
    }

    @Test
    void runningTwiceOnTheSameDay_doesNotDuplicateSlots() {
        setClock("2026-10-05T12:00:00Z");
        Seed seed = seed(2);

        job.extendAll();
        List<Instant> afterFirst = startsOf(seed.coachId());
        job.extendAll();

        assertEquals(12, afterFirst.size());
        assertEquals(afterFirst, startsOf(seed.coachId()));
        assertEquals(12, afterFirst.stream().distinct().count());
    }

    @Test
    void advancingOneDay_createsOnlyTheNewlyOpenedDay() {
        setClock("2026-10-05T12:00:00Z");
        Seed seed = seed(1);
        job.extendAll();
        assertEquals(12, startsOf(seed.coachId()).size());

        // Terça 06/10: a janela passa a cobrir até 02/11, uma segunda.
        setClock("2026-10-06T12:00:00Z");
        job.extendAll();

        List<Instant> starts = startsOf(seed.coachId());
        assertEquals(15, starts.size());
        assertEquals(List.of(saoPaulo("2026-11-02", 18), saoPaulo("2026-11-02", 19), saoPaulo("2026-11-02", 20)),
                starts.subList(12, 15));
        assertEquals(15, starts.stream().distinct().count());
    }

    @Test
    void blockedDays_doNotGetSlots_whetherForAllClubsOrForTheBlocksClub() {
        setClock("2026-10-05T12:00:00Z");
        Seed seed = seed(2);
        QuarkusTransaction.requiringNew().run(() -> {
            Coach coach = coachRepository.findById(seed.coachId());
            // 13/10 bloqueado em todos os clubes; 20/10 só neste clube; 27/10 só em outro clube (não vale aqui).
            dayBlockRepository.persist(DayBlock.create(coach, null, LocalDate.parse("2026-10-13"), null));
            dayBlockRepository.persist(DayBlock.create(coach, clubRepository.findById(seed.clubId()),
                    LocalDate.parse("2026-10-20"), null));
            Club other = Club.create("Outro " + UUID.randomUUID());
            clubRepository.persist(other);
            dayBlockRepository.persist(DayBlock.create(coach, other, LocalDate.parse("2026-10-27"), null));
        });

        job.extendAll();

        List<Instant> starts = startsOf(seed.coachId());
        assertEquals(6, starts.size());
        assertEquals(List.of(saoPaulo("2026-10-06", 18), saoPaulo("2026-10-06", 19), saoPaulo("2026-10-06", 20),
                saoPaulo("2026-10-27", 18), saoPaulo("2026-10-27", 19), saoPaulo("2026-10-27", 20)), starts);
    }

    @Test
    void removedBlocksAndUnlinkedCoaches_areNotExtended() {
        setClock("2026-10-05T12:00:00Z");
        Seed removed = seed(2);
        Seed unlinked = seed(2);
        QuarkusTransaction.requiringNew().run(() -> {
            scheduleBlockRepository.findById(removed.blockId()).markExcluded();
            clubCoachRepository.findByClubAndCoach(unlinked.clubId(), unlinked.coachId()).orElseThrow().markExcluded();
        });

        job.extendAll();

        assertTrue(startsOf(removed.coachId()).isEmpty());
        assertTrue(startsOf(unlinked.coachId()).isEmpty());
    }

    @Test
    void aBlockThatConflictsWithAnotherDoesNotStopTheRest() {
        setClock("2026-10-05T12:00:00Z");
        long[] ids = QuarkusTransaction.requiringNew().call(() -> {
            ClubCoach first = newClubCoach("Conflito A");
            newBlock(first, 2);
            // Mesmo professor em outro clube, com o mesmo horário: gerar este bloco viola o EXCLUDE.
            ClubCoach clash = ClubCoach.create(newClubCoach("Conflito B").getClub(), first.getCoach());
            clash.setPriceSinglesCents(10000L);
            clubCoachRepository.persist(clash);
            newBlock(clash, 2);
            ClubCoach healthy = newClubCoach("Saudável");
            newBlock(healthy, 2);
            return new long[]{first.getCoach().getId(), healthy.getCoach().getId()};
        });

        job.extendAll();

        // O primeiro bloco ganhou os 12 horários; o conflitante foi desfeito; o saudável não foi afetado.
        assertEquals(12, startsOf(ids[0]).size());
        assertEquals(12, startsOf(ids[1]).size());
    }
}
