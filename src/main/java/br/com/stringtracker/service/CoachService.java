package br.com.stringtracker.service;

import br.com.stringtracker.dto.AffectedBookingResponse;
import br.com.stringtracker.dto.CoachOffersResponse;
import br.com.stringtracker.dto.CoachPricesResponse;
import br.com.stringtracker.dto.UnlinkCoachResponse;
import br.com.stringtracker.dto.UpdateCoachOffersRequest;
import br.com.stringtracker.dto.UpdateCoachPricesRequest;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonSlotStatus;
import br.com.stringtracker.model.schedule.ScheduleBlock;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.ClubCoachRepository;
import br.com.stringtracker.repository.LessonSlotRepository;
import br.com.stringtracker.repository.ScheduleBlockRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.Clock;
import java.util.List;

/**
 * Tipos de aula oferecidos (pelo professor), preços por clube e saída do professor do clube (pelo admin do clube).
 */
@ApplicationScoped
public class CoachService {

    @Inject
    ClubAccessService access;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ClubCoachRepository clubCoachRepository;

    @Inject
    LessonSlotRepository lessonSlotRepository;

    @Inject
    ScheduleBlockRepository scheduleBlockRepository;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    BookingCancellationService cancellationService;

    @Inject
    Clock clock;

    @Transactional
    public CoachOffersResponse updateOffers(UpdateCoachOffersRequest request) {
        Coach coach = access.requireCurrentCoach();
        coach.setOffersSingles(request.singles());
        coach.setOffersDoubles(request.doubles());
        coach.setOffersGroup(request.group());
        return CoachOffersResponse.from(coach);
    }

    @Transactional
    public CoachPricesResponse updatePrices(long clubId, long coachId, UpdateCoachPricesRequest request) {
        access.requireClubAdmin(clubId);
        ClubCoach link = clubCoachRepository.findByClubAndCoach(clubId, coachId)
                .filter(ClubCoach::isActive)
                .orElseThrow(() -> new NotFoundException("Professor não vinculado a este clube"));
        link.setPriceSinglesCents(request.singlesCents());
        link.setPriceDoublesCents(request.doublesCents());
        link.setPriceGroupCents(request.groupCents());
        return CoachPricesResponse.from(link);
    }

    /**
     * Desvincula o professor do clube (PROF-06). Sem {@code confirm} lista as aulas futuras afetadas e não muda nada;
     * com {@code confirm} cancela-as com reembolso integral, remove os horários futuros e os blocos do clube e inativa o
     * vínculo. O vínculo do professor com outros clubes não é tocado. A linha do professor é travada antes dos horários.
     */
    @Transactional
    public UnlinkCoachResponse unlink(long clubId, long coachId, boolean confirm) {
        access.requireClubAdmin(clubId);
        ClubCoach link = clubCoachRepository.findByClubAndCoach(clubId, coachId)
                .filter(ClubCoach::isActive)
                .orElseThrow(() -> new NotFoundException("Professor não vinculado a este clube"));
        return confirm ? applyUnlink(link) : previewUnlink(link);
    }

    private UnlinkCoachResponse previewUnlink(ClubCoach link) {
        List<LessonSlot> slots = lessonSlotRepository.listUpcomingOfLink(link.getId(), clock.instant(),
                LockModeType.NONE);
        return response(false, bookingRepository.listActiveOfSlots(LessonSlot.idsOf(slots)));
    }

    private UnlinkCoachResponse applyUnlink(ClubCoach link) {
        lessonSlotRepository.lockCoachSchedule(link.getCoach());
        List<LessonSlot> slots = lessonSlotRepository.listUpcomingOfLink(link.getId(), clock.instant(),
                LockModeType.PESSIMISTIC_WRITE);
        List<Long> bookingIds = bookingRepository.listActiveIdsOfSlots(LessonSlot.idsOf(slots));
        List<Booking> cancelled = cancellationService.cancelAllWithFullRefund(bookingIds,
                currentUserService.requireCurrentUser());
        slots.forEach(slot -> slot.setStatus(LessonSlotStatus.REMOVED));
        scheduleBlockRepository.listActiveOfLink(link.getId()).forEach(ScheduleBlock::markExcluded);
        link.markExcluded();
        return response(true, cancelled);
    }

    private static UnlinkCoachResponse response(boolean applied, List<Booking> bookings) {
        return new UnlinkCoachResponse(applied, bookings.stream().map(AffectedBookingResponse::from).toList());
    }
}
