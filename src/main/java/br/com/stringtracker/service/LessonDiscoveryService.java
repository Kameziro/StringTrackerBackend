package br.com.stringtracker.service;

import br.com.stringtracker.dto.LessonClubResponse;
import br.com.stringtracker.dto.LessonClubSummaryResponse;
import br.com.stringtracker.dto.LessonSlotOption;
import br.com.stringtracker.model.City;
import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.repository.ClubCoachRepository;
import br.com.stringtracker.repository.ClubPhotoRepository;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.repository.LessonSlotRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Descoberta de clubes e professores pelo aluno (DISC-01..04, CLUB-06). Só entra o que o aluno consegue reservar:
 * clube e vínculo ativos e horários livres que ainda aceitam reserva (a mais de 2h do início).
 */
@ApplicationScoped
public class LessonDiscoveryService {

    /** Quantos horários livres aparecem por professor na página do clube. */
    static final int NEXT_SLOTS = 5;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ClubRepository clubRepository;

    @Inject
    ClubPhotoRepository photoRepository;

    @Inject
    ClubCoachRepository clubCoachRepository;

    @Inject
    LessonSlotRepository lessonSlotRepository;

    @Inject
    MinioObjectStorage storage;

    @Inject
    Clock clock;

    /** Clubes da cidade pedida, ou da cidade do perfil do aluno se {@code cityId} for nulo, com horário livre. */
    @Transactional
    public List<LessonClubSummaryResponse> listClubs(Long cityId) {
        long city = cityId != null ? cityId : cityOfProfile();
        return lessonSlotRepository.listBookableClubsOfCity(city, bookableFrom()).stream()
                .map(entry -> LessonClubSummaryResponse.from(entry.club(), entry.nextFreeAt(), storage))
                .toList();
    }

    @Transactional
    public LessonClubResponse getClub(long clubId) {
        currentUserService.requireCurrentUser();
        Club club = clubRepository.findActiveById(clubId).orElseThrow(LinkUnavailableException::new);
        Map<Long, List<LessonSlotOption>> slotsByCoach = lessonSlotRepository.listBookableOfClub(clubId, bookableFrom())
                .stream()
                .collect(Collectors.groupingBy(slot -> slot.getCoach().getId(),
                        Collectors.mapping(LessonSlotOption::from, Collectors.toList())));
        List<LessonClubResponse.CoachEntry> coaches = clubCoachRepository.listActiveOfClub(clubId).stream()
                .map(link -> LessonClubResponse.CoachEntry.from(link,
                        slotsByCoach.getOrDefault(link.getCoach().getId(), List.of()).stream().limit(NEXT_SLOTS).toList(),
                        storage))
                .toList();
        return LessonClubResponse.from(club, photoRepository.listByClub(clubId), coaches, storage);
    }

    /** Primeiro início que ainda aceita reserva: as reservas fecham 2h antes da aula. */
    Instant bookableFrom() {
        return clock.instant().plus(BookingService.BOOKING_CLOSES_BEFORE_START);
    }

    private long cityOfProfile() {
        City city = currentUserService.requireCurrentUser().getCity();
        if (city == null) {
            throw new BadRequestException("Informe a cidade");
        }
        return city.getId();
    }
}
