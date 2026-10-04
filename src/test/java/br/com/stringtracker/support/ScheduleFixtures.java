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
import br.com.stringtracker.model.schedule.LessonType;
import br.com.stringtracker.model.schedule.PaymentMode;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.ClubAdminRepository;
import br.com.stringtracker.repository.ClubCoachRepository;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.repository.CoachRepository;
import br.com.stringtracker.repository.LessonSlotRepository;
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
    ClubAdminRepository clubAdminRepository;

    @Inject
    CoachRepository coachRepository;

    @Inject
    ClubCoachRepository clubCoachRepository;

    @Inject
    LessonSlotRepository lessonSlotRepository;

    @Inject
    BookingRepository bookingRepository;

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
        Coach coach = Coach.create(user("kc-fixture-coach-" + UUID.randomUUID()));
        coach.setOffersSingles(true);
        coach.setOffersDoubles(true);
        coach.setOffersGroup(false);
        coachRepository.persist(coach);
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
