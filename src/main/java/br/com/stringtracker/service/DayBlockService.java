package br.com.stringtracker.service;

import br.com.stringtracker.dto.AffectedBookingResponse;
import br.com.stringtracker.dto.DayBlockRequest;
import br.com.stringtracker.dto.DayBlockResponse;
import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.User;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.DayBlock;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonSlotStatus;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.ClubCoachRepository;
import br.com.stringtracker.repository.DayBlockRepository;
import br.com.stringtracker.repository.LessonSlotRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Bloqueio de um dia do professor (AGND-04, PROFAPP-03). O admin bloqueia o dia só no clube dele; o professor, em
 * todos os clubes. Sem confirmação a chamada só lista as reservas afetadas; com confirmação os horários do dia ainda
 * por começar viram BLOCKED e as reservas deles são canceladas com reembolso integral. O dia fica registrado, e a
 * geração de horários passa a pulá-lo. A linha do professor é travada antes de mexer nos horários.
 */
@ApplicationScoped
public class DayBlockService {

    @Inject
    ClubAccessService access;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ClubCoachRepository clubCoachRepository;

    @Inject
    LessonSlotRepository lessonSlotRepository;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    DayBlockRepository dayBlockRepository;

    @Inject
    BookingCancellationService cancellationService;

    @Inject
    Clock clock;

    @Transactional
    public DayBlockResponse blockForClub(long clubId, long coachId, DayBlockRequest request) {
        access.requireClubAdmin(clubId);
        ClubCoach link = clubCoachRepository.findByClubAndCoach(clubId, coachId)
                .filter(ClubCoach::isActive)
                .orElseThrow(() -> new NotFoundException("Professor não vinculado a este clube"));
        return block(link.getCoach(), link.getClub(), request);
    }

    @Transactional
    public DayBlockResponse blockForCoach(DayBlockRequest request) {
        return block(access.requireCurrentCoach(), null, request);
    }

    /** {@code club} nulo bloqueia o dia em todos os clubes do professor. */
    private DayBlockResponse block(Coach coach, Club club, DayBlockRequest request) {
        if (request.date().isBefore(LocalDate.now(clock))) {
            throw new BusinessRuleException("Não é possível bloquear um dia que já passou");
        }
        return request.confirm() ? apply(coach, club, request.date()) : preview(coach, club, request.date());
    }

    private DayBlockResponse preview(Coach coach, Club club, LocalDate day) {
        List<LessonSlot> slots = upcomingSlots(coach, club, day, LockModeType.NONE);
        return response(day, false, bookingRepository.listActiveOfSlots(LessonSlot.idsOf(slots)));
    }

    private DayBlockResponse apply(Coach coach, Club club, LocalDate day) {
        lessonSlotRepository.lockCoachSchedule(coach);
        List<LessonSlot> slots = upcomingSlots(coach, club, day, LockModeType.PESSIMISTIC_WRITE);
        User actor = currentUserService.requireCurrentUser();
        slots.forEach(slot -> slot.setStatus(LessonSlotStatus.BLOCKED));
        if (!dayBlockRepository.exists(coach.getId(), idOf(club), day)) {
            dayBlockRepository.persist(DayBlock.create(coach, club, day, actor));
        }
        List<Long> bookingIds = bookingRepository.listActiveIdsOfSlots(LessonSlot.idsOf(slots));
        return response(day, true, cancellationService.cancelAllWithFullRefund(bookingIds, actor));
    }

    /** Horários do dia que ainda não começaram, no clube dado ou em todos se for nulo. */
    private List<LessonSlot> upcomingSlots(Coach coach, Club club, LocalDate day, LockModeType lock) {
        return lessonSlotRepository.listUpcomingOfCoachOnDay(coach.getId(), idOf(club),
                day.atStartOfDay(clock.getZone()).toInstant(), day.plusDays(1).atStartOfDay(clock.getZone()).toInstant(),
                clock.instant(), lock);
    }

    private static Long idOf(Club club) {
        return club == null ? null : club.getId();
    }

    private static DayBlockResponse response(LocalDate day, boolean applied, List<Booking> bookings) {
        return new DayBlockResponse(day, applied, bookings.stream().map(AffectedBookingResponse::from).toList());
    }
}
