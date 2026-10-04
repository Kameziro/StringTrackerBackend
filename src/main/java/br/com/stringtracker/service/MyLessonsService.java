package br.com.stringtracker.service;

import br.com.stringtracker.dto.MyLessonsResponse;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.repository.BookingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Aulas do aluno na aba Agenda, junto com os jogos (BOOK-10, MANUAL-02): as pagas com Pix e as manuais vinculadas. */
@ApplicationScoped
public class MyLessonsService {

    /** Quantas aulas passadas voltam, as mais recentes. */
    static final int PAST_LIMIT = 50;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    MinioObjectStorage storage;

    @Inject
    Clock clock;

    @Transactional
    public MyLessonsResponse list() {
        Instant now = clock.instant();
        List<Booking> bookings = bookingRepository.listLessonsOfStudent(currentUserService.requireCurrentUser().getId());
        Map<Boolean, List<Booking>> byUpcoming = bookings.stream().collect(Collectors.partitioningBy(
                booking -> booking.getStatus() == BookingStatus.CONFIRMED
                        && booking.getLessonSlot().getEndsAt().isAfter(now)));
        return new MyLessonsResponse(
                byUpcoming.get(true).stream().map(booking -> MyLessonsResponse.Lesson.from(booking, storage)).toList(),
                byUpcoming.get(false).stream()
                        .sorted(Comparator.comparing((Booking booking) -> booking.getLessonSlot().getStartsAt()).reversed())
                        .limit(PAST_LIMIT)
                        .map(booking -> MyLessonsResponse.Lesson.from(booking, storage))
                        .toList());
    }
}
