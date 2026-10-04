package br.com.stringtracker.service;

import br.com.stringtracker.dto.CreateScheduleBlockRequest;
import br.com.stringtracker.dto.ScheduleBlockResponse;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.schedule.LessonKind;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.ScheduleBlock;
import br.com.stringtracker.repository.ClubCoachRepository;
import br.com.stringtracker.repository.DayBlockRepository;
import br.com.stringtracker.repository.LessonSlotRepository;
import br.com.stringtracker.repository.ScheduleBlockRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.sql.SQLException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

/**
 * Blocos semanais de aula particular e a geração dos horários concretos numa janela móvel de 28 dias.
 * Quem impede dois horários sobrepostos do mesmo professor é o banco (EXCLUDE, AD-014); aqui a violação vira 409.
 */
@ApplicationScoped
public class ScheduleService {

    private static final int WINDOW_DAYS = 28;

    private static final String EXCLUSION_VIOLATION = "23P01";
    private static final short PRIVATE_CAPACITY = 1;

    @Inject
    ClubAccessService access;

    @Inject
    ClubCoachRepository clubCoachRepository;

    @Inject
    ScheduleBlockRepository scheduleBlockRepository;

    @Inject
    LessonSlotRepository lessonSlotRepository;

    @Inject
    DayBlockRepository dayBlockRepository;

    @Inject
    Clock clock;

    @Transactional
    public ScheduleBlockResponse createBlock(long clubId, long coachId, CreateScheduleBlockRequest request) {
        access.requireClubAdmin(clubId);
        ClubCoach link = clubCoachRepository.findByClubAndCoach(clubId, coachId)
                .filter(ClubCoach::isActive)
                .orElseThrow(() -> new NotFoundException("Professor não vinculado a este clube"));
        requireBookablePrice(link);
        requireWholeLessons(request);

        ScheduleBlock block = ScheduleBlock.create(link, LessonKind.PRIVATE, (short) request.dayOfWeek(),
                request.startTime(), request.endTime(), (short) request.durationMinutes(), PRIVATE_CAPACITY);
        scheduleBlockRepository.persist(block);
        return ScheduleBlockResponse.from(block, generateSlots(block));
    }

    /**
     * Desativa o bloco (o job para de estendê-lo) e remove só os horários futuros sem reserva ativa;
     * os horários com reserva e os já passados continuam como estão.
     */
    @Transactional
    public void removeBlock(long clubId, long blockId) {
        access.requireClubAdmin(clubId);
        ScheduleBlock block = scheduleBlockRepository.findActiveById(blockId)
                .orElseThrow(() -> new NotFoundException("Bloco não encontrado"));
        if (block.getClubCoach().getClub().getId() != clubId) {
            throw new ForbiddenException("Este bloco é de outro clube");
        }
        block.markExcluded();
        lessonSlotRepository.removeFutureWithoutBooking(blockId, clock.instant());
    }

    /** Estende o bloco ativo até o fim da janela de 28 dias, sem repetir horários. Devolve quantos criou. */
    @Transactional
    public int extendBlock(long blockId) {
        return scheduleBlockRepository.findActiveById(blockId).map(this::generateSlots).orElse(0);
    }

    /**
     * Cria os horários do bloco nos próximos {@value #WINDOW_DAYS} dias (a partir de hoje) que caem no
     * dia da semana dele, ignorando os que já começaram, os que já existem e os dias bloqueados do professor.
     * Sobreposição com outro horário do professor, em qualquer clube, desfaz a transação inteira com
     * {@link SlotConflictException}.
     */
    private int generateSlots(ScheduleBlock block) {
        // Serializa quem cria horários do mesmo professor: duas transações inserindo horários conflitantes
        // ao mesmo tempo se travariam uma à outra no EXCLUDE (deadlock) em vez de uma receber o 409.
        lessonSlotRepository.lockCoachSchedule(block.getClubCoach().getCoach());
        Instant now = clock.instant();
        LocalDate from = LocalDate.now(clock);
        LocalDate until = from.plusDays(WINDOW_DAYS);
        Set<Instant> existing = lessonSlotRepository.startsAtOfBlock(block.getId(),
                from.atStartOfDay(clock.getZone()).toInstant(), until.atStartOfDay(clock.getZone()).toInstant());
        Set<LocalDate> blockedDays = dayBlockRepository.blockedDays(block.getClubCoach().getCoach().getId(),
                block.getClubCoach().getClub().getId(), from, until);
        Duration lesson = Duration.ofMinutes(block.getDurationMinutes());
        int created = 0;
        try {
            LocalDate day = from.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(block.getDayOfWeek())));
            for (; day.isBefore(until); day = day.plusWeeks(1)) {
                for (LocalTime start = block.getStartTime(); fits(start, lesson, block.getEndTime());
                     start = start.plus(lesson)) {
                    Instant startsAt = ZonedDateTime.of(day, start, clock.getZone()).toInstant();
                    if (startsAt.isAfter(now) && !existing.contains(startsAt) && !blockedDays.contains(day)) {
                        lessonSlotRepository.persist(LessonSlot.create(block, block.getClubCoach(), startsAt,
                                startsAt.plus(lesson), block.getKind(), block.getCapacity()));
                        created++;
                    }
                }
            }
            lessonSlotRepository.flush();
        } catch (PersistenceException e) {
            if (isExclusionViolation(e)) {
                throw new SlotConflictException("O professor já tem compromisso nesse horário", e);
            }
            throw e;
        }
        return created;
    }

    private static boolean fits(LocalTime start, Duration lesson, LocalTime end) {
        LocalTime lessonEnd = start.plus(lesson);
        return lessonEnd.isAfter(start) && !lessonEnd.isAfter(end);
    }

    // Aula particular só vale com singles ou duplas; o preço de grupo não abre agenda particular.
    private static void requireBookablePrice(ClubCoach link) {
        Coach coach = link.getCoach();
        boolean singles = coach.isOffersSingles() && link.getPriceSinglesCents() != null;
        boolean doubles = coach.isOffersDoubles() && link.getPriceDoublesCents() != null;
        if (!singles && !doubles) {
            throw new BusinessRuleException("Defina os preços do professor antes de abrir a agenda");
        }
    }

    private static void requireWholeLessons(CreateScheduleBlockRequest request) {
        if (request.durationMinutes() != 60 && request.durationMinutes() != 90) {
            throw new BadRequestException("A duração deve ser de 60 ou 90 minutos");
        }
        long span = Duration.between(request.startTime(), request.endTime()).toMinutes();
        if (span <= 0 || span % request.durationMinutes() != 0) {
            throw new BadRequestException("O intervalo do bloco deve ter um número exato de aulas");
        }
    }

    private static boolean isExclusionViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && EXCLUSION_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
