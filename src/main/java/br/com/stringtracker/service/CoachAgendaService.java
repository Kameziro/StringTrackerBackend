package br.com.stringtracker.service;

import br.com.stringtracker.dto.CoachAgendaResponse;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.model.schedule.SlotAvailability;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.LessonSlotRepository;
import br.com.stringtracker.repository.PaymentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Agenda do professor no app: os horários e as aulas de todos os clubes onde ele atende (PROFAPP-01, PROFAPP-02). */
@ApplicationScoped
public class CoachAgendaService {

    /** Sem datas, a agenda mostra a semana que começa hoje. */
    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 62;

    @Inject
    ClubAccessService access;

    @Inject
    LessonSlotRepository lessonSlotRepository;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    Clock clock;

    /** {@code from} e {@code to} (AAAA-MM-DD) são inclusivos; sem eles, de hoje até daqui a 6 dias. */
    @Transactional
    public CoachAgendaResponse agenda(String fromText, String toText) {
        Coach coach = access.requireCurrentCoach();
        LocalDate from = fromText == null || fromText.isBlank() ? LocalDate.now(clock) : DatePeriod.parseDate(fromText);
        LocalDate to = toText == null || toText.isBlank() ? from.plusDays(DEFAULT_DAYS - 1) : DatePeriod.parseDate(toText);
        DatePeriod period = DatePeriod.of(from, to, MAX_DAYS);

        List<LessonSlot> slots = lessonSlotRepository.listOfCoach(coach.getId(), period.start(clock), period.end(clock));
        List<Booking> bookings = bookingRepository.listActiveOfSlots(LessonSlot.idsOf(slots));
        Map<Long, PaymentStatus> payments = paymentRepository.statusByBookingId(
                bookings.stream().map(Booking::getId).toList());
        Map<Long, List<Booking>> bookingsBySlot = bookings.stream()
                .collect(Collectors.groupingBy(booking -> booking.getLessonSlot().getId()));

        return new CoachAgendaResponse(from, to, slots.stream().map(slot -> {
            List<Booking> ofSlot = bookingsBySlot.getOrDefault(slot.getId(), List.of());
            long held = ofSlot.stream().filter(booking -> booking.getStatus() == BookingStatus.HELD).count();
            return CoachAgendaResponse.Slot.from(slot, SlotAvailability.of(slot, held, ofSlot.size() - held),
                    ofSlot.stream().map(booking -> CoachAgendaResponse.Lesson.from(booking,
                            payments.get(booking.getId()))).toList());
        }).toList());
    }
}
