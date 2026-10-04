package br.com.stringtracker.resource.admin;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.Payment;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.model.schedule.RefundStatus;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.PaymentRepository;
import br.com.stringtracker.service.ClockProducer;
import br.com.stringtracker.service.ExpoPushService;
import br.com.stringtracker.service.PaymentProviderUnavailableException;
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
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CANC-05: o admin do clube reinicia as tentativas de um reembolso FAILED ou o marca como resolvido por fora. */
@QuarkusTest
class RefundActionsResourceTest {

    private static final String ADMIN = "kc-refund-admin";
    private static final String OTHER_ADMIN = "kc-refund-other-admin";
    private static final String STUDENT = "kc-refund-admin-student";
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final ClubCredentials CREDENTIALS = new ClubCredentials("club-token");

    @InjectMock
    PaymentGateway gateway;

    @InjectMock
    ExpoPushService push;

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    PaymentRepository paymentRepository;

    private long clubId;
    private long otherClubId;
    private long studentId;
    private ClubCoach link;
    private int slotCount;

    @BeforeEach
    void seed() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
        slotCount = 0;
        when(gateway.refreshIfNeeded(any(Club.class))).thenReturn(CREDENTIALS);
        QuarkusTransaction.requiringNew().run(() -> {
            Club club = fixtures.connectPayments(fixtures.club("Reembolso Admin"), NOW.plus(Duration.ofDays(30)));
            Club other = fixtures.club("Reembolso Outro");
            fixtures.admin(club, fixtures.user(ADMIN));
            fixtures.admin(other, fixtures.user(OTHER_ADMIN));
            studentId = fixtures.user(STUDENT).getId();
            link = fixtures.link(club, fixtures.coach(), 9000L, null);
            clubId = club.getId();
            otherClubId = other.getId();
        });
    }

    private record Refund(long bookingId, String orderId) {
    }

    /** Aula Pix cancelada de 90 reais cujo reembolso está no estado dado, com as tentativas já esgotadas. */
    private Refund cancelledPix(RefundStatus status) {
        return QuarkusTransaction.requiringNew().call(() -> {
            String orderId = "ORD-" + UUID.randomUUID();
            Instant lesson = NOW.plus(Duration.ofDays(2)).plus(Duration.ofHours(2L * slotCount++));
            Booking booking = fixtures.pixBooking(fixtures.slot(link, lesson), fixtures.user(STUDENT),
                    BookingStatus.CANCELLED, PaymentStatus.APPROVED, orderId);
            booking.setRefundStatus(status);
            booking.setRefundAmountCents(9000L);
            paymentRepository.findByBookingId(booking.getId()).orElseThrow()
                    .setRefundAttempts(RefundService.MAX_ATTEMPTS);
            return new Refund(booking.getId(), orderId);
        });
    }

    private void inBooking(long bookingId, Consumer<Booking> check) {
        QuarkusTransaction.requiringNew().run(() -> check.accept(bookingRepository.findById(bookingId)));
    }

    private void inPayment(long bookingId, Consumer<Payment> check) {
        QuarkusTransaction.requiringNew().run(() ->
                check.accept(paymentRepository.findByBookingId(bookingId).orElseThrow()));
    }

    private String retry(long club, long bookingId) {
        return "/api/admin/clubs/" + club + "/bookings/" + bookingId + "/refund/retry";
    }

    private String resolve(long club, long bookingId) {
        return "/api/admin/clubs/" + club + "/bookings/" + bookingId + "/refund/resolve";
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void retry_triesAtOnceAndWhenTheProviderAcceptsTheRefundIsDone_andTheStudentIsTold() {
        Refund refund = cancelledPix(RefundStatus.FAILED);

        given().when().post(retry(clubId, refund.bookingId()))
                .then().statusCode(200)
                .body("status", equalTo("CANCELLED"))
                .body("refundStatus", equalTo("DONE"))
                .body("refundAmountCents", equalTo(9000));

        verify(gateway).refund(CREDENTIALS, refund.orderId(), "refund-" + refund.bookingId());
        inPayment(refund.bookingId(), payment -> {
            assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
            assertEquals(1, payment.getRefundAttempts());
            assertNull(payment.getNextRefundAt());
        });
        verify(push).notifyUser(studentId, "Reembolso concluído",
                "O valor de R$ 90,00 da aula de 07/10 às 09:00 foi devolvido.",
                Map.of("type", "lesson_refunded", "bookingId", String.valueOf(refund.bookingId())));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void retry_whenTheProviderFailsAgain_goesBackToPendingWithAFreshCountAndTheNextAttemptInFifteenMinutes() {
        Refund refund = cancelledPix(RefundStatus.FAILED);
        doThrow(new PaymentProviderUnavailableException("fora do ar", null))
                .when(gateway).refund(any(), anyString(), anyString());

        given().when().post(retry(clubId, refund.bookingId()))
                .then().statusCode(200).body("refundStatus", equalTo("PENDING"));

        inPayment(refund.bookingId(), payment -> {
            assertEquals(PaymentStatus.APPROVED, payment.getStatus());
            assertEquals(1, payment.getRefundAttempts());
            assertEquals(NOW.plus(RefundService.RETRY_INTERVAL), payment.getNextRefundAt());
        });
        verify(push, never()).notifyUser(anyLong(), anyString(), anyString(), any());
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void resolve_marksTheRefundResolvedManuallyWithWhoAndWhen_withoutCallingTheProvider() {
        Refund refund = cancelledPix(RefundStatus.FAILED);

        given().when().post(resolve(clubId, refund.bookingId()))
                .then().statusCode(200)
                .body("refundStatus", equalTo("RESOLVED_MANUALLY"))
                .body("refundAmountCents", equalTo(9000));

        verify(gateway, never()).refund(any(), anyString(), anyString());
        inBooking(refund.bookingId(), booking -> {
            assertEquals(RefundStatus.RESOLVED_MANUALLY, booking.getRefundStatus());
            assertEquals(ADMIN, booking.getRefundResolvedBy().getKeycloakId());
            assertEquals(NOW, booking.getRefundResolvedAt());
        });
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void bothActions_onlyApplyToAFailedRefund() {
        for (RefundStatus status : new RefundStatus[]{RefundStatus.NONE, RefundStatus.PENDING, RefundStatus.DONE,
                RefundStatus.RESOLVED_MANUALLY}) {
            Refund refund = cancelledPix(status);

            given().when().post(retry(clubId, refund.bookingId())).then().statusCode(422)
                    .body(equalTo("Só um reembolso que falhou pode ser reiniciado ou resolvido manualmente"));
            given().when().post(resolve(clubId, refund.bookingId())).then().statusCode(422);

            inBooking(refund.bookingId(), booking -> assertEquals(status, booking.getRefundStatus()));
        }

        verify(gateway, never()).refund(any(), anyString(), anyString());
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void aSecondCallAfterTheFirstOneWorked_isRejected() {
        Refund resolved = cancelledPix(RefundStatus.FAILED);
        given().when().post(resolve(clubId, resolved.bookingId())).then().statusCode(200);
        given().when().post(resolve(clubId, resolved.bookingId())).then().statusCode(422);

        Refund retried = cancelledPix(RefundStatus.FAILED);
        given().when().post(retry(clubId, retried.bookingId())).then().statusCode(200);
        given().when().post(retry(clubId, retried.bookingId())).then().statusCode(422);
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void unknownBooking_returns404() {
        given().when().post(retry(clubId, 999999999L)).then().statusCode(404);
        given().when().post(resolve(clubId, 999999999L)).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = OTHER_ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = OTHER_ADMIN)})
    void adminOfAnotherClub_gets403_bothForTheBookingsClubAndForTheirOwn() {
        Refund refund = cancelledPix(RefundStatus.FAILED);

        given().when().post(retry(clubId, refund.bookingId())).then().statusCode(403);
        given().when().post(resolve(clubId, refund.bookingId())).then().statusCode(403);
        given().when().post(retry(otherClubId, refund.bookingId())).then().statusCode(403);
        given().when().post(resolve(otherClubId, refund.bookingId())).then().statusCode(403);

        inBooking(refund.bookingId(), booking -> assertEquals(RefundStatus.FAILED, booking.getRefundStatus()));
        verify(gateway, never()).refund(any(), anyString(), anyString());
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void userWhoIsNotAnAdmin_gets403() {
        Refund refund = cancelledPix(RefundStatus.FAILED);

        given().when().post(retry(clubId, refund.bookingId())).then().statusCode(403);
        given().when().post(resolve(clubId, refund.bookingId())).then().statusCode(403);

        inBooking(refund.bookingId(), booking -> assertEquals(RefundStatus.FAILED, booking.getRefundStatus()));
    }

    @Test
    void withoutAToken_returns401() {
        given().when().post(retry(clubId, 1L)).then().statusCode(401);
        given().when().post(resolve(clubId, 1L)).then().statusCode(401);
    }
}
