package br.com.stringtracker.resource;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.model.schedule.RefundStatus;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.service.ClockProducer;
import br.com.stringtracker.support.ScheduleFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

@QuarkusTest
class MyLessonsResourceTest {

    private static final String STUDENT = "kc-mylessons-student";
    private static final String OTHER = "kc-mylessons-other";
    private static final String NEWCOMER = "kc-mylessons-newcomer";
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    BookingRepository bookingRepository;

    private ClubCoach link;

    @BeforeEach
    void seed() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
        QuarkusTransaction.requiringNew().run(() -> {
            // O aluno é o mesmo em todos os testes: as reservas dos testes anteriores saem da conta dele.
            bookingRepository.update("active = false where studentUser.id = ?1", fixtures.user(STUDENT).getId());
            fixtures.user(OTHER);
            fixtures.user(NEWCOMER);
            Club club = fixtures.club("Minhas Aulas");
            club.setAddress("Rua A, 1");
            link = fixtures.link(club, fixtures.coach(), 9000L, 12000L);
        });
    }

    private long pix(Duration startsIn, BookingStatus status, PaymentStatus payment, String student) {
        return QuarkusTransaction.requiringNew().call(() -> fixtures.pixBooking(
                fixtures.slot(link, NOW.plus(startsIn)), fixtures.user(student), status, payment,
                "ORD-" + UUID.randomUUID()).getId());
    }

    private long manual(Duration startsIn, BookingStatus status, String student) {
        return QuarkusTransaction.requiringNew().call(() ->
                fixtures.studentBooking(fixtures.slot(link, NOW.plus(startsIn)), status, fixtures.user(student))
                        .getId());
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void listsOnlyTheStudentsLessons_pixAndManual_splitIntoUpcomingAndPast() {
        long ended = pix(Duration.ofDays(-3), BookingStatus.CONFIRMED, PaymentStatus.APPROVED, STUDENT);
        long ongoing = pix(Duration.ofMinutes(-30), BookingStatus.CONFIRMED, PaymentStatus.APPROVED, STUDENT);
        long upcomingPix = pix(Duration.ofDays(1), BookingStatus.CONFIRMED, PaymentStatus.APPROVED, STUDENT);
        long upcomingManual = manual(Duration.ofDays(2), BookingStatus.CONFIRMED, STUDENT);
        pix(Duration.ofDays(3), BookingStatus.HELD, PaymentStatus.PENDING, STUDENT);
        pix(Duration.ofDays(4), BookingStatus.EXPIRED, PaymentStatus.EXPIRED, STUDENT);
        pix(Duration.ofDays(6), BookingStatus.CONFIRMED, PaymentStatus.APPROVED, OTHER);
        long cancelled = pix(Duration.ofDays(5), BookingStatus.CANCELLED, PaymentStatus.REFUNDED, STUDENT);
        QuarkusTransaction.requiringNew().run(() -> {
            Booking booking = bookingRepository.findById(cancelled);
            booking.setCancelledAt(NOW);
            booking.setRefundStatus(RefundStatus.DONE);
            booking.setRefundAmountCents(9000L);
        });

        given().when().get("/api/me/lessons")
                .then().statusCode(200)
                .body("upcoming.bookingId", contains((int) ongoing, (int) upcomingPix, (int) upcomingManual))
                .body("past.bookingId", contains((int) cancelled, (int) ended))
                .body("upcoming[1].status", equalTo("CONFIRMED"))
                .body("upcoming[1].paymentMode", equalTo("PIX"))
                .body("upcoming[1].priceCents", equalTo(9000))
                .body("upcoming[1].clubName", startsWith("Minhas Aulas"))
                .body("upcoming[1].clubAddress", equalTo("Rua A, 1"))
                .body("upcoming[1].coachName", startsWith("kc-fixture-coach-"))
                .body("upcoming[1].startsAt", equalTo("2026-10-06T12:00:00Z"))
                .body("upcoming[2].paymentMode", equalTo("OFFLINE"));
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void confirmedLessonsTellUntilWhenACancellationRefundsInFull_cancelledOnesTellWhatWasRefunded() {
        pix(Duration.ofDays(1), BookingStatus.CONFIRMED, PaymentStatus.APPROVED, STUDENT);
        long cancelled = pix(Duration.ofDays(5), BookingStatus.CANCELLED, PaymentStatus.REFUNDED, STUDENT);
        QuarkusTransaction.requiringNew().run(() -> {
            Booking booking = bookingRepository.findById(cancelled);
            booking.setCancelledAt(NOW);
            booking.setRefundStatus(RefundStatus.DONE);
            booking.setRefundAmountCents(9000L);
        });

        given().when().get("/api/me/lessons")
                .then().statusCode(200)
                .body("upcoming[0].fullRefundUntil", equalTo("2026-10-05T12:00:00Z"))
                .body("upcoming[0].cancelledAt", nullValue())
                .body("past[0].status", equalTo("CANCELLED"))
                .body("past[0].fullRefundUntil", nullValue())
                .body("past[0].cancelledAt", equalTo("2026-10-05T12:00:00Z"))
                .body("past[0].refundStatus", equalTo("DONE"))
                .body("past[0].refundAmountCents", equalTo(9000));
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void pastLessonsAreCappedAtTheFiftyMostRecent() {
        QuarkusTransaction.requiringNew().run(() -> {
            for (int i = 1; i <= 55; i++) {
                fixtures.studentBooking(fixtures.slot(link, NOW.minus(Duration.ofHours(2L * i))),
                        BookingStatus.CONFIRMED, fixtures.user(STUDENT));
            }
        });

        given().when().get("/api/me/lessons")
                .then().statusCode(200)
                .body("upcoming", empty())
                .body("past", hasSize(50))
                .body("past[0].startsAt", equalTo("2026-10-05T10:00:00Z"));
    }

    @Test
    @TestSecurity(user = NEWCOMER)
    @JwtSecurity(claims = {@Claim(key = "sub", value = NEWCOMER)})
    void studentWithoutLessons_getsEmptyLists() {
        given().when().get("/api/me/lessons")
                .then().statusCode(200).body("upcoming", empty()).body("past", empty());
    }

    @Test
    void withoutLogin_returns401() {
        given().when().get("/api/me/lessons").then().statusCode(401);
    }
}
