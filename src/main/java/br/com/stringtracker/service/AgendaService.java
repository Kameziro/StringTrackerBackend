package br.com.stringtracker.service;

import br.com.stringtracker.dto.AgendaResponse;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.SlotAvailability;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.BookingRepository.ActiveSeat;
import br.com.stringtracker.repository.ClubCoachRepository;
import br.com.stringtracker.repository.LessonSlotRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Grade semanal do clube para o painel (AGND-06). */
@ApplicationScoped
public class AgendaService {

    @Inject
    ClubAccessService access;

    @Inject
    ClubCoachRepository clubCoachRepository;

    @Inject
    LessonSlotRepository lessonSlotRepository;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    Clock clock;

    /** A semana vai de segunda a domingo e contém o dia {@code week} (AAAA-MM-DD); sem ele, a semana de hoje. */
    @Transactional
    public AgendaResponse weekAgenda(long clubId, String week) {
        access.requireClubAdmin(clubId);
        LocalDate monday = parse(week).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant from = monday.atStartOfDay(clock.getZone()).toInstant();
        Instant until = monday.plusWeeks(1).atStartOfDay(clock.getZone()).toInstant();

        Map<Long, List<ActiveSeat>> seatsBySlot = bookingRepository.listActiveSeats(clubId, from, until).stream()
                .collect(Collectors.groupingBy(ActiveSeat::slotId));
        List<AgendaResponse.Slot> slots = lessonSlotRepository.listOfClub(clubId, from, until).stream()
                .map(slot -> AgendaResponse.Slot.from(slot,
                        availability(slot, seatsBySlot.getOrDefault(slot.getId(), List.of()))))
                .toList();
        List<AgendaResponse.Coach> coaches = clubCoachRepository.listActiveOfClub(clubId).stream()
                .map(AgendaResponse.Coach::from)
                .toList();
        return new AgendaResponse(monday, monday.plusDays(6), coaches, slots);
    }

    private LocalDate parse(String week) {
        if (week == null || week.isBlank()) {
            return LocalDate.now(clock);
        }
        try {
            return LocalDate.parse(week);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Semana inválida: use o formato AAAA-MM-DD");
        }
    }

    private static SlotAvailability availability(LessonSlot slot, List<ActiveSeat> seats) {
        long held = seats.stream().filter(seat -> seat.status() == BookingStatus.HELD).count();
        return SlotAvailability.of(slot, held, seats.size() - held);
    }
}
