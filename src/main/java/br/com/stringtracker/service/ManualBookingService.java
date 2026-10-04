package br.com.stringtracker.service;

import br.com.stringtracker.dto.BookingResponse;
import br.com.stringtracker.dto.CreateManualBookingRequest;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.User;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonSlotStatus;
import br.com.stringtracker.model.schedule.LessonType;
import br.com.stringtracker.model.schedule.PaymentMode;
import br.com.stringtracker.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.time.Clock;

/**
 * Reserva manual: aula combinada fora do app, registrada pelo admin do clube ou pelo próprio professor num horário
 * livre da agenda dele. Nasce confirmada, com pagamento por fora (sem Pix). O aluno é uma conta da plataforma ou,
 * sem conta, um nome com telefone. Vale até o início da aula, sem a regra das 2h, que é só do aluno.
 */
@ApplicationScoped
public class ManualBookingService {

    private static final String SLOT_TAKEN = "Esse horário não está mais livre";

    @Inject
    ClubAccessService access;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    SeatService seatService;

    @Inject
    UserRepository userRepository;

    @Inject
    Clock clock;

    /** Quem faz a aula: uma conta ({@code account}) ou um convidado com nome e telefone. */
    private record Student(User account, String guestName, String guestPhone) {
    }

    @Transactional
    public BookingResponse createForClub(long clubId, CreateManualBookingRequest request) {
        access.requireClubAdmin(clubId);
        Student student = resolveStudent(request);
        LessonSlot slot = seatService.lockSlot(request.slotId());
        if (slot.getClubCoach().getClub().getId() != clubId) {
            throw new ForbiddenException("Este horário é de outro clube");
        }
        return create(slot, student, request);
    }

    @Transactional
    public BookingResponse createForCoach(CreateManualBookingRequest request) {
        Coach coach = access.requireCurrentCoach();
        Student student = resolveStudent(request);
        LessonSlot slot = seatService.lockSlot(request.slotId());
        if (!slot.getCoach().getId().equals(coach.getId())) {
            throw new ForbiddenException("Este horário é de outro professor");
        }
        return create(slot, student, request);
    }

    private BookingResponse create(LessonSlot slot, Student student, CreateManualBookingRequest request) {
        if (slot.getStatus() == LessonSlotStatus.BLOCKED) {
            throw new SlotConflictException(SLOT_TAKEN);
        }
        if (!clock.instant().isBefore(slot.getStartsAt())) {
            throw new BusinessRuleException("A aula já começou");
        }
        LessonType type = request.type();
        ClubCoach link = LessonOffer.requireOffered(slot, type);
        // Sem preço na tabela do clube o valor foi combinado por fora: a reserva manual não exige preço definido.
        Long price = link.priceOf(type);

        Booking booking = Booking.create(slot, seatService.freeSeat(slot, SLOT_TAKEN), type, price == null ? 0 : price,
                PaymentMode.OFFLINE, BookingStatus.CONFIRMED, currentUserService.requireCurrentUser());
        booking.setStudentUser(student.account());
        booking.setGuestName(student.guestName());
        booking.setGuestPhone(student.guestPhone());
        if (type == LessonType.DOUBLES && hasText(request.partnerName())) {
            booking.setPartnerName(request.partnerName().trim());
        }
        seatService.persist(booking, SLOT_TAKEN);
        return BookingResponse.from(booking, null);
    }

    private Student resolveStudent(CreateManualBookingRequest request) {
        boolean hasGuestData = hasText(request.guestName()) || hasText(request.guestPhone());
        if (request.studentUserId() != null) {
            if (hasGuestData) {
                throw new BadRequestException("Informe o aluno com conta ou o nome e o telefone, não os dois");
            }
            User account = userRepository.findByIdOptional(request.studentUserId())
                    .filter(User::isActive)
                    .orElseThrow(() -> new NotFoundException("Aluno não encontrado"));
            return new Student(account, null, null);
        }
        if (!hasText(request.guestName()) || !hasText(request.guestPhone())) {
            throw new BadRequestException("Informe o aluno com conta ou o nome e o telefone");
        }
        return new Student(null, request.guestName().trim(),
                PhoneNumber.digitsOf(request.guestPhone(), "Telefone inválido"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
