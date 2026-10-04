package br.com.stringtracker.service;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.model.schedule.RefundStatus;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.PaymentRepository;
import br.com.stringtracker.service.payment.PaymentGateway;
import br.com.stringtracker.service.payment.PaymentGateway.ClubCredentials;
import br.com.stringtracker.service.payment.RefundService;
import br.com.stringtracker.support.ScheduleFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
class LessonNotifierTest {

    private static final String ADMIN = "kc-notify-admin";
    private static final String COACH = "kc-notify-coach";
    private static final String STUDENT = "kc-notify-student";
    private static final String OTHER_STUDENT = "kc-notify-other-student";
    private static final String COMMON = "kc-notify-common";

    /** Segunda-feira 2026-10-05, 09:00 em São Paulo; as aulas são quarta-feira 2026-10-07 às 10:00. */
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final Instant LESSON = Instant.parse("2026-10-07T13:00:00Z");
    private static final String WHEN = "07/10 às 10:00";
    private static final ClubCredentials CREDENTIALS = new ClubCredentials("club-token");

    @InjectMock
    ExpoPushService push;

    @InjectMock
    PaymentGateway gateway;

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    BookingService bookingService;

    @Inject
    RefundService refundService;

    @Inject
    LessonNotifier notifier;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    PaymentRepository paymentRepository;

    private long clubId;
    private long coachId;
    private String clubName;
    private ClubCoach link;
    private long adminId;
    private long coachUserId;
    private long studentId;
    private long otherStudentId;
    private int slotCount;

    /** O professor COACH atende num clube com Pix conectado; ADMIN administra o clube. */
    @BeforeEach
    void seed() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
        when(gateway.refreshIfNeeded(any(Club.class))).thenReturn(CREDENTIALS);
        slotCount = 0;
        QuarkusTransaction.requiringNew().run(() -> {
            Club club = fixtures.connectPayments(fixtures.club("Notifica Clube"), NOW.plus(Duration.ofDays(30)));
            adminId = fixtures.user(ADMIN).getId();
            fixtures.admin(club, fixtures.user(ADMIN));
            coachUserId = fixtures.user(COACH).getId();
            studentId = fixtures.user(STUDENT).getId();
            otherStudentId = fixtures.user(OTHER_STUDENT).getId();
            fixtures.user(COMMON);
            link = fixtures.link(club, fixtures.coach(fixtures.user(COACH)), 9000L, 12000L);
            coachId = link.getCoach().getId();
            clubId = club.getId();
            clubName = club.getName();
        });
    }

    private LessonSlot nextSlot() {
        return fixtures.slot(link, LESSON.plus(Duration.ofHours(2L * slotCount++)));
    }

    private long pixBooking(BookingStatus status, PaymentStatus payment, String student) {
        return QuarkusTransaction.requiringNew().call(() ->
                fixtures.pixBooking(nextSlot(), fixtures.user(student), status, payment, "ORD-" + UUID.randomUUID())
                        .getId());
    }

    private long offlineBooking(String student) {
        return QuarkusTransaction.requiringNew().call(() ->
                fixtures.studentBooking(nextSlot(), BookingStatus.CONFIRMED, fixtures.user(student)).getId());
    }

    private <T> T inBooking(long bookingId, Function<Booking, T> read) {
        return QuarkusTransaction.requiringNew().call(() -> read.apply(bookingRepository.findById(bookingId)));
    }

    private void applyPayment(long bookingId, PaymentStatus remote) {
        QuarkusTransaction.requiringNew().run(() -> {
            bookingRepository.findById(bookingId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            bookingService.applyPaymentStatus(paymentRepository.findByBookingId(bookingId).orElseThrow(), remote);
        });
    }

    private String coachName() {
        return COACH;
    }

    private static Map<String, Object> data(String type, long bookingId) {
        return Map.of("type", type, "bookingId", String.valueOf(bookingId));
    }

    @Test
    void pixPaidInTime_notifiesTheStudentOfTheConfirmationAndTheCoachOfTheNewBooking() {
        long bookingId = pixBooking(BookingStatus.HELD, PaymentStatus.PENDING, STUDENT);

        applyPayment(bookingId, PaymentStatus.APPROVED);

        verify(push).notifyUser(studentId, "Aula confirmada", coachName() + " · " + WHEN + " · " + clubName,
                data("lesson_confirmed", bookingId));
        verify(push).notifyUser(coachUserId, "Nova aula reservada", STUDENT + " · " + WHEN + " · " + clubName,
                data("coach_booking", bookingId));
        verifyNoMoreInteractions(push);
    }

    @Test
    void pixPaidAfterTheSlotWasReleased_refundsAndTellsTheStudentOnly() {
        long bookingId = pixBooking(BookingStatus.EXPIRED, PaymentStatus.EXPIRED, STUDENT);

        applyPayment(bookingId, PaymentStatus.APPROVED);

        verify(gateway).refund(eq(CREDENTIALS), anyString(), eq("refund-" + bookingId));
        verify(push).notifyUser(studentId, "Pagamento fora do prazo",
                "Pagamento recebido após o prazo: valor devolvido", data("lesson_refunded", bookingId));
        verifyNoMoreInteractions(push);
    }

    @Test
    void pixPaidAfterTheSlotWasReleasedWithTheRefundPending_saysTheValueWillBeReturned() {
        doThrow(new PaymentProviderUnavailableException("timeout", null)).when(gateway)
                .refund(any(), anyString(), anyString());
        long bookingId = pixBooking(BookingStatus.EXPIRED, PaymentStatus.EXPIRED, STUDENT);

        applyPayment(bookingId, PaymentStatus.APPROVED);

        verify(push).notifyUser(studentId, "Pagamento fora do prazo",
                "Pagamento recebido após o prazo: valor será devolvido", data("lesson_refunded", bookingId));
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void studentCancelling_notifiesTheCoachAndNobodyElse() {
        long bookingId = pixBooking(BookingStatus.CONFIRMED, PaymentStatus.APPROVED, STUDENT);

        given().when().post("/api/lessons/bookings/" + bookingId + "/cancel").then().statusCode(200);

        verify(push).notifyUser(coachUserId, "Aula cancelada pelo aluno", STUDENT + " cancelou a aula de " + WHEN + ".",
                data("coach_booking_cancelled", bookingId));
        verifyNoMoreInteractions(push);
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void adminCancelling_tellsTheStudentAboutTheRefundAndTheCoachAboutTheCancellation_butNeverTheAdmin() {
        long bookingId = pixBooking(BookingStatus.CONFIRMED, PaymentStatus.APPROVED, STUDENT);

        given().when().post("/api/admin/clubs/" + clubId + "/bookings/" + bookingId + "/cancel")
                .then().statusCode(200);

        verify(push).notifyUser(studentId, "Aula cancelada",
                "Sua aula com " + coachName() + " de " + WHEN + " foi cancelada. O valor de R$ 90,00 foi devolvido.",
                data("lesson_cancelled", bookingId));
        verify(push).notifyUser(coachUserId, "Aula cancelada pelo clube",
                "A aula de " + WHEN + " com " + STUDENT + " foi cancelada pelo clube.",
                data("coach_booking_cancelled", bookingId));
        verifyNoMoreInteractions(push);
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void adminCancellingAHeldBooking_tellsNobody_becauseTheCoachNeverSawIt() {
        long bookingId = pixBooking(BookingStatus.HELD, PaymentStatus.PENDING, STUDENT);

        given().when().post("/api/admin/clubs/" + clubId + "/bookings/" + bookingId + "/cancel")
                .then().statusCode(200);

        verify(push, never()).notifyUser(anyLong(), anyString(), anyString(), anyMap());
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void coachCancellingAManualBooking_tellsTheStudentWithoutAnyRefundSentence() {
        long bookingId = offlineBooking(STUDENT);

        given().when().post("/api/coach/me/bookings/" + bookingId + "/cancel").then().statusCode(200);

        verify(push).notifyUser(studentId, "Aula cancelada",
                "Sua aula com " + coachName() + " de " + WHEN + " foi cancelada.",
                data("lesson_cancelled", bookingId));
        verifyNoMoreInteractions(push);
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void blockingADay_tellsEveryConfirmedStudent_butNotThoseStillPaying() {
        long confirmed = pixBooking(BookingStatus.CONFIRMED, PaymentStatus.APPROVED, STUDENT);
        long held = pixBooking(BookingStatus.HELD, PaymentStatus.PENDING, OTHER_STUDENT);

        given().contentType(ContentType.JSON).body("{\"date\":\"2026-10-07\",\"confirm\":true}")
                .when().post("/api/coach/me/day-blocks").then().statusCode(200);

        verify(push).notifyUser(eq(studentId), eq("Aula cancelada"), anyString(), eq(data("lesson_cancelled", confirmed)));
        verify(push, never()).notifyUser(eq(otherStudentId), anyString(), anyString(), anyMap());
        verifyNoMoreInteractions(push);
        assertEquals(BookingStatus.CANCELLED, inBooking(held, Booking::getStatus));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void unlinkingTheCoach_tellsTheStudentsOfTheCancelledLessons() {
        long bookingId = pixBooking(BookingStatus.CONFIRMED, PaymentStatus.APPROVED, STUDENT);

        given().when().delete("/api/admin/clubs/" + clubId + "/coaches/" + coachId + "?confirm=true")
                .then().statusCode(200);

        verify(push).notifyUser(eq(studentId), eq("Aula cancelada"), anyString(), eq(data("lesson_cancelled", bookingId)));
        verifyNoMoreInteractions(push);
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void adminManualBooking_tellsTheCoachAndTheStudentWithAnAccount_butForAGuestOnlyTheCoach() {
        long slotForAccount = QuarkusTransaction.requiringNew().call(() -> nextSlot().getId());
        long slotForGuest = QuarkusTransaction.requiringNew().call(() -> nextSlot().getId());

        long accountBooking = given().contentType(ContentType.JSON)
                .body("{\"slotId\":%d,\"type\":\"SINGLES\",\"studentUserId\":%d}".formatted(slotForAccount, studentId))
                .when().post("/api/admin/clubs/" + clubId + "/bookings")
                .then().statusCode(201).extract().jsonPath().getLong("bookingId");
        long guestBooking = given().contentType(ContentType.JSON)
                .body("{\"slotId\":%d,\"type\":\"SINGLES\",\"guestName\":\"Ana\",\"guestPhone\":\"98999990000\"}"
                        .formatted(slotForGuest))
                .when().post("/api/admin/clubs/" + clubId + "/bookings")
                .then().statusCode(201).extract().jsonPath().getLong("bookingId");

        verify(push).notifyUser(eq(studentId), eq("Aula confirmada"), anyString(),
                eq(data("lesson_confirmed", accountBooking)));
        verify(push).notifyUser(coachUserId, "Nova aula reservada pelo clube",
                STUDENT + " · 07/10 às 10:00 · " + clubName, data("coach_booking", accountBooking));
        verify(push).notifyUser(coachUserId, "Nova aula reservada pelo clube",
                "Ana · 07/10 às 12:00 · " + clubName, data("coach_booking", guestBooking));
        verifyNoMoreInteractions(push);
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void coachManualBooking_tellsTheStudentButNotTheCoachWhoDidIt() {
        long slotId = QuarkusTransaction.requiringNew().call(() -> nextSlot().getId());

        long bookingId = given().contentType(ContentType.JSON)
                .body("{\"slotId\":%d,\"type\":\"SINGLES\",\"studentUserId\":%d}".formatted(slotId, studentId))
                .when().post("/api/coach/me/bookings")
                .then().statusCode(201).extract().jsonPath().getLong("bookingId");

        verify(push).notifyUser(eq(studentId), eq("Aula confirmada"), anyString(),
                eq(data("lesson_confirmed", bookingId)));
        verifyNoMoreInteractions(push);
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void adminBlockingADay_tellsTheCoachOnceWithTheCancelledCount_andEachConfirmedStudent() {
        long first = pixBooking(BookingStatus.CONFIRMED, PaymentStatus.APPROVED, STUDENT);
        pixBooking(BookingStatus.CONFIRMED, PaymentStatus.APPROVED, OTHER_STUDENT);

        given().contentType(ContentType.JSON).body("{\"date\":\"2026-10-07\",\"confirm\":true}")
                .when().post("/api/admin/clubs/" + clubId + "/coaches/" + coachId + "/day-blocks")
                .then().statusCode(200);

        verify(push).notifyUser(coachUserId, "Dia bloqueado pelo clube",
                clubName + " bloqueou 07/10 na sua agenda. 2 reservas foram canceladas.",
                Map.of("type", "coach_day_blocked", "date", "2026-10-07"));
        verify(push).notifyUser(eq(studentId), eq("Aula cancelada"), anyString(), eq(data("lesson_cancelled", first)));
        verify(push).notifyUser(eq(otherStudentId), eq("Aula cancelada"), anyString(), anyMap());
        verifyNoMoreInteractions(push);
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void adminBlockingADayWithoutBookings_stillTellsTheCoach_butAPreviewTellsNobody() {
        String url = "/api/admin/clubs/" + clubId + "/coaches/" + coachId + "/day-blocks";

        given().contentType(ContentType.JSON).body("{\"date\":\"2026-10-07\"}").when().post(url).then().statusCode(200);
        verify(push, never()).notifyUser(anyLong(), anyString(), anyString(), anyMap());

        given().contentType(ContentType.JSON).body("{\"date\":\"2026-10-07\",\"confirm\":true}")
                .when().post(url).then().statusCode(200);
        verify(push).notifyUser(coachUserId, "Dia bloqueado pelo clube",
                clubName + " bloqueou 07/10 na sua agenda.", Map.of("type", "coach_day_blocked", "date", "2026-10-07"));
        verifyNoMoreInteractions(push);
    }

    @Test
    @TestSecurity(user = COACH)
    @JwtSecurity(claims = {@Claim(key = "sub", value = COACH)})
    void anAdminOfTheClubNeverGetsAPush_evenWhenTheyAreTheStudent() {
        long bookingId = QuarkusTransaction.requiringNew().call(() -> fixtures.studentBooking(nextSlot(),
                BookingStatus.CONFIRMED, fixtures.user(ADMIN)).getId());

        given().when().post("/api/coach/me/bookings/" + bookingId + "/cancel").then().statusCode(200);

        verify(push, never()).notifyUser(anyLong(), anyString(), anyString(), anyMap());
        assertEquals(Long.valueOf(adminId), inBooking(bookingId, booking -> booking.getStudentUser().getId()));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void aGuestBookingCancelled_stillCancels_andOnlyTheCoachGetsAPush() {
        long bookingId = QuarkusTransaction.requiringNew().call(() ->
                fixtures.booking(nextSlot(), BookingStatus.CONFIRMED, fixtures.user(ADMIN)).getId());

        given().when().post("/api/admin/clubs/" + clubId + "/bookings/" + bookingId + "/cancel")
                .then().statusCode(200).body("status", org.hamcrest.Matchers.equalTo("CANCELLED"));

        verify(push).notifyUser(eq(coachUserId), eq("Aula cancelada pelo clube"), anyString(),
                eq(data("coach_booking_cancelled", bookingId)));
        verifyNoMoreInteractions(push);
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void aFailingPush_doesNotChangeTheBooking() {
        doThrow(new IllegalStateException("expo fora do ar")).when(push)
                .notifyUser(anyLong(), anyString(), anyString(), anyMap());
        long bookingId = pixBooking(BookingStatus.CONFIRMED, PaymentStatus.APPROVED, STUDENT);

        given().when().post("/api/admin/clubs/" + clubId + "/bookings/" + bookingId + "/cancel")
                .then().statusCode(200).body("status", org.hamcrest.Matchers.equalTo("CANCELLED"));

        verify(push).notifyUser(eq(studentId), eq("Aula cancelada"), anyString(), anyMap());
        inBooking(bookingId, booking -> {
            assertEquals(BookingStatus.CANCELLED, booking.getStatus());
            assertEquals(RefundStatus.DONE, booking.getRefundStatus());
            return null;
        });
    }

    @Test
    void aRolledBackTransaction_sendsNoPush() {
        long bookingId = pixBooking(BookingStatus.CONFIRMED, PaymentStatus.APPROVED, STUDENT);

        assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            notifier.bookingConfirmed(bookingRepository.findById(bookingId));
            throw new IllegalStateException("desfaz");
        }));

        verify(push, never()).notifyUser(anyLong(), anyString(), anyString(), anyMap());
    }

    @Test
    void aRefundThatSucceedsOnRetry_tellsTheStudentItWasReturned() {
        long bookingId = pixBooking(BookingStatus.CANCELLED, PaymentStatus.APPROVED, STUDENT);
        long paymentId = QuarkusTransaction.requiringNew().call(() -> {
            Booking booking = bookingRepository.findById(bookingId);
            booking.setRefundStatus(RefundStatus.PENDING);
            booking.setRefundAmountCents(9000L);
            return paymentRepository.findByBookingId(bookingId).orElseThrow().getId();
        });

        QuarkusTransaction.requiringNew().run(() -> refundService.retry(paymentId));

        verify(push).notifyUser(studentId, "Reembolso concluído",
                "O valor de R$ 90,00 da aula de " + WHEN + " foi devolvido.", data("lesson_refunded", bookingId));
        verifyNoMoreInteractions(push);
    }
}
