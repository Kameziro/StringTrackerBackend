package br.com.stringtracker.resource.admin;

import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.User;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.model.schedule.RefundStatus;
import br.com.stringtracker.service.ClockProducer;
import br.com.stringtracker.support.ScheduleFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ClubBookingsResourceTest {

    private static final String ADMIN = "kc-clubbookings-admin";
    private static final String OTHER_ADMIN = "kc-clubbookings-other-admin";
    private static final String COMMON = "kc-clubbookings-common";
    private static final String STUDENT = "kc-clubbookings-student";

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    EntityManagerFactory entityManagerFactory;

    private long clubId;
    private String coachName;
    private long coachId;
    private long studentId;
    private Booking confirmedPix;
    private Booking offlineGuest;
    private Booking cancelledRefunded;
    private Booking heldPix;
    private Booking expiredPix;

    /**
     * Semana de 05 a 11/10 (UTC-3): cinco reservas de estados diferentes dentro dela, mais uma de outro clube,
     * uma desativada e as que caem um minuto fora das bordas do período.
     */
    @BeforeEach
    void seed() {
        QuarkusMock.installMockForType(Clock.fixed(Instant.parse("2026-10-07T15:00:00Z"), ClockProducer.ZONE),
                Clock.class);
        QuarkusTransaction.requiringNew().run(() -> {
            User admin = fixtures.user(ADMIN);
            User student = fixtures.user(STUDENT);
            fixtures.user(COMMON);
            String order = "ORD-CB-" + UUID.randomUUID() + "-";
            var club = fixtures.club("Reservas Clube");
            var otherClub = fixtures.club("Reservas Outro");
            fixtures.admin(club, admin);
            fixtures.admin(otherClub, fixtures.user(OTHER_ADMIN));
            Coach coach = fixtures.coach();
            Coach elsewhere = fixtures.coach();
            ClubCoach link = fixtures.link(club, coach, 9000L, null);
            ClubCoach otherLink = fixtures.link(otherClub, elsewhere, 9000L, null);

            confirmedPix = fixtures.pixBooking(fixtures.slot(link, Instant.parse("2026-10-06T15:00:00Z")), student,
                    BookingStatus.CONFIRMED, PaymentStatus.APPROVED, order + "1");
            confirmedPix.setPartnerName("Bia");
            offlineGuest = fixtures.booking(fixtures.slot(link, Instant.parse("2026-10-07T15:00:00Z")),
                    BookingStatus.CONFIRMED, admin);
            offlineGuest.setGuestName("Ana Convidada");
            offlineGuest.setGuestPhone("98999990000");
            cancelledRefunded = fixtures.pixBooking(fixtures.slot(link, Instant.parse("2026-10-08T15:00:00Z")),
                    student, BookingStatus.CANCELLED, PaymentStatus.REFUNDED, order + "2");
            cancelledRefunded.setCancelledAt(Instant.parse("2026-10-07T12:00:00Z"));
            cancelledRefunded.setCancelledBy(admin);
            cancelledRefunded.setRefundStatus(RefundStatus.DONE);
            cancelledRefunded.setRefundAmountCents(9000L);
            heldPix = fixtures.pixBooking(fixtures.slot(link, Instant.parse("2026-10-09T15:00:00Z")), student,
                    BookingStatus.HELD, PaymentStatus.PENDING, order + "3");
            expiredPix = fixtures.pixBooking(fixtures.slot(link, Instant.parse("2026-10-10T15:00:00Z")), student,
                    BookingStatus.EXPIRED, PaymentStatus.EXPIRED, order + "4");

            // Bordas: um minuto antes de domingo 04 acabar e exatamente 00:00 de 12/10 em São Paulo ficam fora da semana.
            fixtures.booking(fixtures.slot(link, Instant.parse("2026-10-05T02:59:00Z")), BookingStatus.CONFIRMED, admin);
            fixtures.booking(fixtures.slot(link, Instant.parse("2026-10-12T03:00:00Z")), BookingStatus.CONFIRMED, admin);
            fixtures.booking(fixtures.slot(otherLink, Instant.parse("2026-10-06T15:00:00Z")),
                    BookingStatus.CONFIRMED, admin);
            fixtures.booking(fixtures.slot(link, Instant.parse("2026-10-11T12:00:00Z")), BookingStatus.CONFIRMED, admin)
                    .markExcluded();

            clubId = club.getId();
            coachId = coach.getId();
            coachName = coach.getUser().getName();
            studentId = student.getId();
        });
    }

    private static RequestSpecification period(String from, String to) {
        var request = given();
        if (from != null) {
            request = request.queryParam("from", from);
        }
        if (to != null) {
            request = request.queryParam("to", to);
        }
        return request;
    }

    private String url() {
        return "/api/admin/clubs/%d/bookings".formatted(clubId);
    }

    private static int id(Booking booking) {
        return booking.getId().intValue();
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void lists_theClubsBookingsOfEveryStatusInThePeriod_byLessonStart() {
        period("2026-10-05", "2026-10-11").when().get(url())
                .then().statusCode(200)
                .body("from", equalTo("2026-10-05"))
                .body("to", equalTo("2026-10-11"))
                .body("bookings.bookingId", contains(id(confirmedPix), id(offlineGuest), id(cancelledRefunded),
                        id(heldPix), id(expiredPix)))
                .body("bookings.status", contains("CONFIRMED", "CONFIRMED", "CANCELLED", "HELD", "EXPIRED"))
                .body("bookings.paymentMode", contains("PIX", "OFFLINE", "PIX", "PIX", "PIX"))
                .body("bookings.paymentStatus", contains("APPROVED", null, "REFUNDED", "PENDING", "EXPIRED"));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void eachItem_carriesTheLessonCoachTypeStudentAndRefund() {
        period("2026-10-05", "2026-10-11").when().get(url())
                .then().statusCode(200)
                .body("bookings[0].slotId", equalTo(confirmedPix.getLessonSlot().getId().intValue()))
                .body("bookings[0].startsAt", equalTo("2026-10-06T15:00:00Z"))
                .body("bookings[0].endsAt", equalTo("2026-10-06T16:00:00Z"))
                .body("bookings[0].coachId", equalTo((int) coachId))
                .body("bookings[0].coachName", equalTo(coachName))
                .body("bookings[0].lessonType", equalTo("SINGLES"))
                .body("bookings[0].priceCents", equalTo(9000))
                .body("bookings[0].partnerName", equalTo("Bia"))
                .body("bookings[0].refundStatus", equalTo("NONE"))
                .body("bookings[0].refundAmountCents", nullValue())
                .body("bookings[0].cancelledAt", nullValue())
                .body("bookings[2].cancelledAt", equalTo("2026-10-07T12:00:00Z"))
                .body("bookings[2].refundStatus", equalTo("DONE"))
                .body("bookings[2].refundAmountCents", equalTo(9000));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void student_withAnAccountHasAMaskedEmail_andAGuestHasThePhoneInstead() {
        period("2026-10-05", "2026-10-11").when().get(url())
                .then().statusCode(200)
                .body("bookings[0].student.userId", equalTo((int) studentId))
                .body("bookings[0].student.name", equalTo(STUDENT))
                .body("bookings[0].student.emailHint", equalTo("k***@example.com"))
                .body("bookings[0].student.phone", nullValue())
                .body("bookings[1].student.name", equalTo("Ana Convidada"))
                .body("bookings[1].student.phone", equalTo("98999990000"))
                .body("bookings[1].student.userId", nullValue())
                .body("bookings[1].student.emailHint", nullValue());
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void period_isInclusiveOnBothEnds_inSaoPauloTime() {
        // Só o dia 06/10: uma reserva. A das 02:59Z é do dia 04 (23:59 em São Paulo) e a das 03:00Z do dia 12 é de segunda 00:00.
        period("2026-10-06", "2026-10-06").when().get(url())
                .then().statusCode(200)
                .body("bookings.bookingId", contains(id(confirmedPix)));
        period("2026-10-05", "2026-10-05").when().get(url())
                .then().statusCode(200)
                .body("bookings", empty());
        period("2026-10-04", "2026-10-04").when().get(url())
                .then().statusCode(200)
                .body("bookings", hasSize(1));
        period("2026-10-12", "2026-10-12").when().get(url())
                .then().statusCode(200)
                .body("bookings", hasSize(1));
        // Um mês sem reservas devolve a lista vazia.
        period("2026-11-01", "2026-11-30").when().get(url())
                .then().statusCode(200)
                .body("bookings", empty());
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void periodOf31DaysIsAllowed_32DaysIsNot() {
        period("2026-10-01", "2026-10-31").when().get(url()).then().statusCode(200);
        period("2026-10-01", "2026-11-01").when().get(url()).then().statusCode(400)
                .body(equalTo("Período inválido: use até 31 dias, com 'to' depois de 'from'"));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void missingReversedOrMalformedDates_return400() {
        period(null, null).when().get(url()).then().statusCode(400);
        period("2026-10-05", null).when().get(url()).then().statusCode(400);
        period(null, "2026-10-11").when().get(url()).then().statusCode(400)
                .body(equalTo("Informe o período: use from e to no formato AAAA-MM-DD"));
        period("2026-10-11", "2026-10-05").when().get(url()).then().statusCode(400);
        period("amanhã", "2026-10-11").when().get(url()).then().statusCode(400)
                .body(equalTo("Data inválida: use o formato AAAA-MM-DD"));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void numberOfQueries_doesNotGrowWithTheNumberOfBookings() {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        period("2026-10-06", "2026-10-06").when().get(url()).then().statusCode(200); // aquece

        long before = statistics.getPrepareStatementCount();
        period("2026-10-06", "2026-10-06").when().get(url()).then().body("bookings", hasSize(1));
        long forOne = statistics.getPrepareStatementCount() - before;

        before = statistics.getPrepareStatementCount();
        period("2026-10-05", "2026-10-11").when().get(url()).then().body("bookings", hasSize(5));
        long forFive = statistics.getPrepareStatementCount() - before;

        assertTrue(forOne > 0, "as estatísticas do Hibernate precisam estar ligadas");
        assertEquals(forOne, forFive);
    }

    @Test
    @TestSecurity(user = OTHER_ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = OTHER_ADMIN)})
    void adminOfAnotherClub_returns403() {
        period("2026-10-05", "2026-10-11").when().get(url()).then().statusCode(403);
    }

    @Test
    @TestSecurity(user = COMMON)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COMMON)})
    void commonUser_returns403() {
        period("2026-10-05", "2026-10-11").when().get(url()).then().statusCode(403);
    }

    @Test
    void withoutAToken_returns401() {
        period("2026-10-05", "2026-10-11").when().get(url()).then().statusCode(401);
    }
}
