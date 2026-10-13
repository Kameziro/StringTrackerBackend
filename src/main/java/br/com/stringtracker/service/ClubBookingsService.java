package br.com.stringtracker.service;

import br.com.stringtracker.dto.ClubBookingsResponse;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.PaymentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Relatório de reservas do clube para o painel (pagamentos e reembolsos por período). */
@ApplicationScoped
public class ClubBookingsService {

    private static final int MAX_DAYS = 31;

    @Inject
    ClubAccessService access;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    Clock clock;

    /** {@code from} e {@code to} (AAAA-MM-DD) são obrigatórios e inclusivos, no máximo 31 dias. */
    @Transactional
    public ClubBookingsResponse list(long clubId, String fromText, String toText) {
        access.requireClubAdmin(clubId);
        DatePeriod period = DatePeriod.of(required(fromText), required(toText), MAX_DAYS);

        List<Booking> bookings = bookingRepository.listOfClub(clubId, period.start(clock), period.end(clock));
        Map<Long, PaymentStatus> payments = paymentRepository.statusByBookingId(
                bookings.stream().map(Booking::getId).toList());
        return new ClubBookingsResponse(period.from(), period.to(), bookings.stream()
                .map(booking -> ClubBookingsResponse.Item.from(booking, payments.get(booking.getId()))).toList());
    }

    private static LocalDate required(String text) {
        if (text == null || text.isBlank()) {
            throw new BadRequestException("Informe o período: use from e to no formato AAAA-MM-DD");
        }
        return DatePeriod.parseDate(text);
    }
}
