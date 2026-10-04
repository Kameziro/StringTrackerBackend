package br.com.stringtracker.service;

import br.com.stringtracker.dto.BookingResponse;
import br.com.stringtracker.dto.CreateBookingRequest;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.User;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonSlotStatus;
import br.com.stringtracker.model.schedule.LessonType;
import br.com.stringtracker.model.schedule.Payment;
import br.com.stringtracker.model.schedule.PaymentMode;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.model.schedule.RefundStatus;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.LessonSlotRepository;
import br.com.stringtracker.repository.PaymentRepository;
import br.com.stringtracker.service.payment.PaymentGateway;
import br.com.stringtracker.service.payment.PaymentGateway.ClubCredentials;
import br.com.stringtracker.service.payment.PaymentGateway.PixCharge;
import br.com.stringtracker.service.payment.RefundService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Reserva de aula paga com Pix. A vaga é segurada por 10 minutos pelo relógio da API; o horário é travado
 * (FOR UPDATE) durante o hold, e o índice único de vaga do banco (V13) continua sendo a garantia final.
 */
@ApplicationScoped
public class BookingService {

    public static final Duration HOLD = Duration.ofMinutes(10);
    public static final Duration BOOKING_CLOSES_BEFORE_START = Duration.ofHours(2);

    private static final Logger LOG = Logger.getLogger(BookingService.class);
    private static final String PROVIDER = "MERCADOPAGO";
    private static final String SEAT_TAKEN = "Esse horário acabou de ser reservado";
    private static final String UNIQUE_VIOLATION = "23505";

    @Inject
    CurrentUserService currentUserService;

    @Inject
    LessonSlotRepository lessonSlotRepository;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    PaymentGateway gateway;

    @Inject
    RefundService refundService;

    @Inject
    Clock clock;

    /**
     * Segura uma vaga do horário e gera o Pix. Tudo acontece numa transação: se o provedor falhar, a vaga
     * segurada desaparece junto (BOOK-08); quem chega durante o hold espera e então recebe 409 (BOOK-05).
     */
    @Transactional
    public BookingResponse hold(CreateBookingRequest request) {
        User student = currentUserService.requireCurrentUser();
        LessonSlot slot = requireBookableSlot(request.slotId());
        ClubCoach link = slot.getClubCoach();
        long price = priceOf(slot, link, request.type());
        PaymentGateway.ClubCredentials credentials = gateway.refreshIfNeeded(link.getClub());

        Instant now = clock.instant();
        Booking booking = Booking.create(slot, freeSeat(slot), request.type(), price, PaymentMode.PIX,
                BookingStatus.HELD, student);
        booking.setStudentUser(student);
        booking.setHoldExpiresAt(now.plus(HOLD));
        if (request.type() == LessonType.DOUBLES) {
            booking.setPartnerName(request.partnerName());
        }
        persistSeat(booking);

        String reference = "booking-" + booking.getId();
        PixCharge charge = gateway.createPix(credentials, price,
                "Aula de padel com " + slot.getCoach().getUser().getName(), student.getEmail(), reference, HOLD,
                reference);
        Payment payment = Payment.create(booking, PROVIDER, price, booking.getHoldExpiresAt());
        payment.setProviderPaymentId(charge.providerOrderId());
        paymentRepository.persist(payment);

        return BookingResponse.from(booking, new BookingResponse.Pix(charge.qrCodeBase64(), charge.qrCode(),
                charge.ticketUrl(), booking.getHoldExpiresAt()));
    }

    /**
     * Expira o hold vencido: a reserva e o pagamento viram EXPIRED, a vaga fica livre para outro aluno e o Pix é
     * cancelado no provedor. O relógio do hold é o da API; falha no cancelamento não impede a expiração, já que o
     * Pix também vence sozinho no provedor e um pagamento tardio é reembolsado pelo webhook.
     */
    @Transactional
    public void expireHold(long bookingId) {
        Booking booking = bookingRepository.findById(bookingId, LockModeType.PESSIMISTIC_WRITE);
        if (booking.getStatus() != BookingStatus.HELD || !booking.getHoldExpiresAt().isBefore(clock.instant())) {
            return;
        }
        booking.setStatus(BookingStatus.EXPIRED);
        paymentRepository.findByBookingId(bookingId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.EXPIRED);
            }
            cancelAtProvider(booking, payment);
        });
    }

    private void cancelAtProvider(Booking booking, Payment payment) {
        try {
            ClubCredentials credentials = gateway.refreshIfNeeded(booking.getLessonSlot().getClubCoach().getClub());
            gateway.cancel(credentials, payment.getProviderPaymentId(), "cancel-" + booking.getId());
        } catch (RuntimeException e) {
            LOG.warnf("Pix da reserva %d não foi cancelado no provedor: %s", booking.getId(), e.getMessage());
        }
    }

    /** A reserva só é visível ao aluno que a fez; para os demais ela não existe. */
    @Transactional
    public BookingResponse get(long bookingId) {
        User student = currentUserService.requireCurrentUser();
        Booking booking = bookingRepository.findByIdAndStudent(bookingId, student.getId())
                .orElseThrow(() -> new NotFoundException("Reserva não encontrada"));
        return BookingResponse.from(booking, null);
    }

    /**
     * Aplica à reserva o estado do pagamento lido no provedor. Quem chama já travou a reserva
     * e passa um pagamento carregado depois do lock. Repetir o mesmo estado não muda nada.
     */
    public void applyPaymentStatus(Payment payment, PaymentStatus remote) {
        switch (remote) {
            case APPROVED -> approve(payment);
            case EXPIRED -> expireUnpaid(payment);
            case REFUNDED -> markRefunded(payment);
            case PENDING -> {
            }
        }
    }

    // Pago a tempo: confirma. Pago depois que o horário foi liberado (BOOK-04): reembolso integral.
    private void approve(Payment payment) {
        if (payment.getStatus() == PaymentStatus.APPROVED || payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        Booking booking = payment.getBooking();
        payment.setStatus(PaymentStatus.APPROVED);
        if (booking.getStatus() == BookingStatus.HELD) {
            booking.setStatus(BookingStatus.CONFIRMED);
        } else {
            refundService.requestRefund(booking);
        }
    }

    private void expireUnpaid(Payment payment) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        payment.setStatus(PaymentStatus.EXPIRED);
        if (payment.getBooking().getStatus() == BookingStatus.HELD) {
            payment.getBooking().setStatus(BookingStatus.EXPIRED);
        }
    }

    private void markRefunded(Payment payment) {
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        Booking booking = payment.getBooking();
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setNextRefundAt(null);
        booking.setRefundStatus(RefundStatus.DONE);
        if (booking.getRefundAmountCents() == null) {
            booking.setRefundAmountCents(payment.getAmountCents());
        }
    }

    private LessonSlot requireBookableSlot(long slotId) {
        LessonSlot slot = lessonSlotRepository.findById(slotId, LockModeType.PESSIMISTIC_WRITE);
        if (slot == null || !slot.isActive() || slot.getStatus() == LessonSlotStatus.REMOVED
                || !slot.getClubCoach().isActive() || !slot.getClubCoach().getClub().isActive()) {
            throw new NotFoundException("Horário não encontrado");
        }
        if (slot.getStatus() == LessonSlotStatus.BLOCKED) {
            throw new SlotConflictException("Esse horário não está mais disponível");
        }
        if (slot.getStartsAt().isBefore(clock.instant().plus(BOOKING_CLOSES_BEFORE_START))) {
            throw new BusinessRuleException("Reservas fecham 2h antes da aula");
        }
        return slot;
    }

    private static long priceOf(LessonSlot slot, ClubCoach link, LessonType type) {
        if (!type.fits(slot.getKind()) || !link.getCoach().offers(type)) {
            throw new BusinessRuleException("O professor não oferece esse tipo de aula neste horário");
        }
        Long price = link.priceOf(type);
        if (price == null) {
            throw new BusinessRuleException("O professor não tem preço definido para esse tipo de aula");
        }
        return price;
    }

    /** Menor vaga livre do horário; sem nenhuma, o horário acabou de ser reservado. */
    private short freeSeat(LessonSlot slot) {
        Set<Short> occupied = bookingRepository.occupiedSeats(slot.getId());
        return IntStream.rangeClosed(1, slot.getCapacity())
                .filter(seat -> !occupied.contains((short) seat))
                .mapToObj(seat -> (short) seat)
                .findFirst()
                .orElseThrow(() -> new SlotConflictException(SEAT_TAKEN));
    }

    private void persistSeat(Booking booking) {
        try {
            bookingRepository.persist(booking);
        } catch (PersistenceException e) {
            if (isUniqueViolation(e)) {
                throw new SlotConflictException(SEAT_TAKEN, e);
            }
            throw e;
        }
    }

    private static boolean isUniqueViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
