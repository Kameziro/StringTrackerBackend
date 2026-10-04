package br.com.stringtracker.support;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubAdmin;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.ClubPaymentStatus;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.User;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonKind;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonSlotStatus;
import br.com.stringtracker.model.schedule.LessonType;
import br.com.stringtracker.model.schedule.Payment;
import br.com.stringtracker.model.schedule.PaymentMode;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.CityRepository;
import br.com.stringtracker.repository.ClubAdminRepository;
import br.com.stringtracker.repository.ClubCoachRepository;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.repository.CoachRepository;
import br.com.stringtracker.repository.LessonSlotRepository;
import br.com.stringtracker.repository.PaymentRepository;
import br.com.stringtracker.repository.UserRepository;
import br.com.stringtracker.service.payment.TokenCipher;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.UUID;

/**
 * Dados de agenda para os testes. Os métodos gravam sem abrir transação: chame-os dentro de
 * {@link QuarkusTransaction#requiringNew()}. Nomes e e-mails são únicos para os testes não se pisarem.
 */
@ApplicationScoped
public class ScheduleFixtures {

    @Inject
    TokenCipher tokenCipher;

    @Inject
    UserRepository userRepository;

    @Inject
    ClubRepository clubRepository;

    @Inject
    CityRepository cityRepository;

    @Inject
    ClubAdminRepository clubAdminRepository;

    @Inject
    CoachRepository coachRepository;

    @Inject
    ClubCoachRepository clubCoachRepository;

    @Inject
    LessonSlotRepository lessonSlotRepository;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    PaymentRepository paymentRepository;

    /** O usuário com esse id do Keycloak, criado se ainda não existir. */
    public User user(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId).orElseGet(() -> {
            User user = new User();
            user.setKeycloakId(keycloakId);
            user.setName(keycloakId);
            user.setEmail(keycloakId + "@example.com");
            userRepository.persist(user);
            return user;
        });
    }

    public Club club(String prefix) {
        Club club = Club.create(prefix + " " + UUID.randomUUID());
        clubRepository.persist(club);
        return club;
    }

    /** Clube numa das cidades semeadas (as buscas por cidade ficam isoladas escolhendo uma cidade só do teste). */
    public Club club(String prefix, long cityId) {
        Club club = club(prefix);
        club.setCity(cityRepository.findById(cityId));
        return club;
    }

    /** Liga a conta Mercado Pago do clube, com tokens válidos até {@code tokenExpiresAt}. */
    public Club connectPayments(Club club, Instant tokenExpiresAt) {
        club.setPaymentStatus(ClubPaymentStatus.CONNECTED);
        club.setMpUserId("123");
        club.setMpAccessTokenEnc(tokenCipher.encrypt("club-access-token"));
        club.setMpRefreshTokenEnc(tokenCipher.encrypt("club-refresh-token"));
        club.setMpTokenExpiresAt(tokenExpiresAt);
        return club;
    }

    public void admin(Club club, User user) {
        clubAdminRepository.persist(ClubAdmin.create(club, user));
    }

    /** Professor que oferece singles e duplas, com um usuário novo. */
    public Coach coach() {
        return coach(user("kc-fixture-coach-" + UUID.randomUUID()));
    }

    /**
     * O professor desse usuário (singles e duplas), criado se ainda não existir. O perfil é um só por usuário,
     * então um professor reaproveitado entre testes chega com a agenda dos testes anteriores removida:
     * o banco não aceita dois horários sobrepostos do mesmo professor.
     */
    public Coach coach(User user) {
        Coach coach = coachRepository.findByUserId(user.getId()).orElseGet(() -> {
            Coach created = Coach.create(user);
            coachRepository.persist(created);
            return created;
        });
        coach.setOffersSingles(true);
        coach.setOffersDoubles(true);
        coach.setOffersGroup(false);
        lessonSlotRepository.update("status = ?1 where coach.id = ?2", LessonSlotStatus.REMOVED, coach.getId());
        return coach;
    }

    public ClubCoach link(Club club, Coach coach, Long singlesCents, Long doublesCents) {
        ClubCoach link = ClubCoach.create(club, coach);
        link.setPriceSinglesCents(singlesCents);
        link.setPriceDoublesCents(doublesCents);
        clubCoachRepository.persist(link);
        return link;
    }

    /** Horário particular avulso (sem bloco), de uma hora. */
    public LessonSlot slot(ClubCoach link, Instant startsAt) {
        LessonSlot slot = LessonSlot.create(null, link, startsAt, startsAt.plusSeconds(3600), LessonKind.PRIVATE,
                (short) 1);
        lessonSlotRepository.persist(slot);
        return slot;
    }

    /** Reserva Pix de singles (R$ 90) na vaga 1 do horário, com o pagamento no estado dado. */
    public Booking pixBooking(LessonSlot slot, User student, BookingStatus status, PaymentStatus paymentStatus,
                              String providerOrderId) {
        Booking booking = Booking.create(slot, (short) 1, LessonType.SINGLES, 9000L, PaymentMode.PIX, status, student);
        booking.setStudentUser(student);
        if (status == BookingStatus.HELD) {
            booking.setHoldExpiresAt(slot.getStartsAt().minusSeconds(3600));
        }
        bookingRepository.persist(booking);
        Payment payment = Payment.create(booking, "MERCADOPAGO", 9000L, slot.getStartsAt());
        payment.setProviderPaymentId(providerOrderId);
        payment.setStatus(paymentStatus);
        paymentRepository.persist(payment);
        return booking;
    }

    /** Reserva de um aluno com conta na vaga 1 do horário, paga por fora. */
    public Booking studentBooking(LessonSlot slot, BookingStatus status, User student) {
        Booking booking = booking(slot, status, student);
        booking.setStudentUser(student);
        return booking;
    }

    /** Reserva de um convidado na vaga 1 do horário, paga por fora. */
    public Booking booking(LessonSlot slot, BookingStatus status, User createdBy) {
        Booking booking = Booking.create(slot, (short) 1, LessonType.SINGLES, 10000L, PaymentMode.OFFLINE, status,
                createdBy);
        booking.setGuestName("Aluno");
        bookingRepository.persist(booking);
        return booking;
    }
}
