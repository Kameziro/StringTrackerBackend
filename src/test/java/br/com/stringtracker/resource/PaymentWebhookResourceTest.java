package br.com.stringtracker.resource;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.schedule.Booking;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.Payment;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.model.schedule.RefundStatus;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.PaymentEventRepository;
import br.com.stringtracker.repository.PaymentRepository;
import br.com.stringtracker.service.ClockProducer;
import br.com.stringtracker.service.PaymentProviderUnavailableException;
import br.com.stringtracker.service.payment.PaymentGateway;
import br.com.stringtracker.service.payment.PaymentGateway.ClubCredentials;
import br.com.stringtracker.service.payment.PaymentGateway.ProviderPayment;
import br.com.stringtracker.support.ScheduleFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class PaymentWebhookResourceTest {

    private static final String URL = "/api/payments/mercadopago/webhook";
    private static final String SECRET = "test-only-webhook-secret";
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final ClubCredentials CREDENTIALS = new ClubCredentials("club-token");
    private static final AtomicLong EVENT_IDS = new AtomicLong(System.nanoTime());

    @InjectMock
    PaymentGateway gateway;

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    PaymentEventRepository paymentEventRepository;

    @BeforeEach
    void setUp() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
        when(gateway.refreshIfNeeded(any(Club.class))).thenReturn(CREDENTIALS);
    }

    private static String newOrderId() {
        return "ORD" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private long booking(String orderId, BookingStatus status, PaymentStatus paymentStatus) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Club club = fixtures.connectPayments(fixtures.club("Webhook Clube"), NOW.plus(Duration.ofDays(30)));
            ClubCoach link = fixtures.link(club, fixtures.coach(), 9000L, null);
            LessonSlot slot = fixtures.slot(link, NOW.plus(Duration.ofDays(2)));
            return fixtures.pixBooking(slot, fixtures.user("kc-webhook-student"), status, paymentStatus, orderId)
                    .getId();
        });
    }

    private static String sign(String dataId, String requestId, String timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";";
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Notificação assinada como o Mercado Pago faz (id do pedido em minúsculas no manifesto). */
    private RequestSpecification signed(String orderId, long eventId) {
        String requestId = "req-" + UUID.randomUUID();
        return given()
                .header("x-signature", "ts=1700000000,v1=" + sign(orderId.toLowerCase(Locale.ROOT), requestId, "1700000000"))
                .header("x-request-id", requestId)
                .queryParam("data.id", orderId)
                .contentType(ContentType.JSON)
                .body("{\"id\":%d,\"type\":\"order\",\"action\":\"order.updated\",\"data\":{\"id\":\"%s\"}}"
                        .formatted(eventId, orderId));
    }

    private void providerSays(String orderId, PaymentStatus status) {
        doReturn(new ProviderPayment(orderId, status)).when(gateway).getOrder(CREDENTIALS, orderId);
    }

    private void check(long bookingId, Consumer<Booking> bookingCheck, Consumer<Payment> paymentCheck) {
        QuarkusTransaction.requiringNew().run(() -> {
            bookingCheck.accept(bookingRepository.findById(bookingId));
            paymentCheck.accept(paymentRepository.findByBookingId(bookingId).orElseThrow());
        });
    }

    private long events(long eventId) {
        return paymentEventRepository.count("providerEventId", "mp-" + eventId);
    }

    @Test
    void approvedWithinTheHold_confirmsTheBooking() {
        String orderId = newOrderId();
        long bookingId = booking(orderId, BookingStatus.HELD, PaymentStatus.PENDING);
        providerSays(orderId, PaymentStatus.APPROVED);

        signed(orderId, EVENT_IDS.incrementAndGet()).when().post(URL).then().statusCode(200);

        check(bookingId, b -> {
            assertEquals(BookingStatus.CONFIRMED, b.getStatus());
            assertEquals(RefundStatus.NONE, b.getRefundStatus());
        }, p -> assertEquals(PaymentStatus.APPROVED, p.getStatus()));
        verify(gateway, never()).refund(any(), anyString(), anyString());
    }

    @Test
    void approvedAfterTheBookingExpired_refundsTheFullAmountAndKeepsTheBookingExpired() {
        String orderId = newOrderId();
        long bookingId = booking(orderId, BookingStatus.EXPIRED, PaymentStatus.EXPIRED);
        providerSays(orderId, PaymentStatus.APPROVED);

        signed(orderId, EVENT_IDS.incrementAndGet()).when().post(URL).then().statusCode(200);

        verify(gateway).refund(CREDENTIALS, orderId, "refund-" + bookingId);
        check(bookingId, b -> {
            assertEquals(BookingStatus.EXPIRED, b.getStatus());
            assertEquals(RefundStatus.DONE, b.getRefundStatus());
            assertEquals(9000L, b.getRefundAmountCents());
        }, p -> assertEquals(PaymentStatus.REFUNDED, p.getStatus()));
    }

    @Test
    void lateRefundThatFails_staysPendingForTheRetryJob() {
        String orderId = newOrderId();
        long bookingId = booking(orderId, BookingStatus.EXPIRED, PaymentStatus.EXPIRED);
        providerSays(orderId, PaymentStatus.APPROVED);
        doThrow(new PaymentProviderUnavailableException("fora do ar", null))
                .when(gateway).refund(any(), eq(orderId), anyString());

        signed(orderId, EVENT_IDS.incrementAndGet()).when().post(URL).then().statusCode(200);

        check(bookingId, b -> assertEquals(RefundStatus.PENDING, b.getRefundStatus()), p -> {
            assertEquals(PaymentStatus.APPROVED, p.getStatus());
            assertEquals(NOW.plus(Duration.ofMinutes(15)), p.getNextRefundAt());
        });
    }

    @Test
    void theSameEventTwice_isProcessedOnce() {
        String orderId = newOrderId();
        long bookingId = booking(orderId, BookingStatus.HELD, PaymentStatus.PENDING);
        providerSays(orderId, PaymentStatus.APPROVED);
        long eventId = EVENT_IDS.incrementAndGet();

        signed(orderId, eventId).when().post(URL).then().statusCode(200);
        signed(orderId, eventId).when().post(URL).then().statusCode(200);

        verify(gateway, times(1)).getOrder(CREDENTIALS, orderId);
        assertEquals(1, events(eventId));
        check(bookingId, b -> assertEquals(BookingStatus.CONFIRMED, b.getStatus()), p -> {
        });
    }

    @Test
    void anotherEventWithTheSameResult_changesNothingAndDoesNotRefundTwice() {
        String paidInTime = newOrderId();
        long confirmed = booking(paidInTime, BookingStatus.HELD, PaymentStatus.PENDING);
        providerSays(paidInTime, PaymentStatus.APPROVED);
        String late = newOrderId();
        long lateBooking = booking(late, BookingStatus.EXPIRED, PaymentStatus.EXPIRED);
        providerSays(late, PaymentStatus.APPROVED);

        for (int delivery = 0; delivery < 2; delivery++) {
            signed(paidInTime, EVENT_IDS.incrementAndGet()).when().post(URL).then().statusCode(200);
            signed(late, EVENT_IDS.incrementAndGet()).when().post(URL).then().statusCode(200);
        }

        check(confirmed, b -> assertEquals(BookingStatus.CONFIRMED, b.getStatus()), p -> {
        });
        verify(gateway, times(1)).refund(any(), eq(late), eq("refund-" + lateBooking));
    }

    @Test
    void invalidSignatures_return401AndProcessNothing() {
        String orderId = newOrderId();
        long bookingId = booking(orderId, BookingStatus.HELD, PaymentStatus.PENDING);
        providerSays(orderId, PaymentStatus.APPROVED);
        long eventId = EVENT_IDS.incrementAndGet();
        String body = "{\"id\":%d,\"data\":{\"id\":\"%s\"}}".formatted(eventId, orderId);
        String lower = orderId.toLowerCase(Locale.ROOT);

        // Hash errado, id adulterado, ts adulterado, cabeçalhos ausentes e malformados.
        given().header("x-signature", "ts=1700000000,v1=" + "0".repeat(64)).header("x-request-id", "r1")
                .queryParam("data.id", orderId).contentType(ContentType.JSON).body(body)
                .when().post(URL).then().statusCode(401);
        given().header("x-signature", "ts=1700000000,v1=" + sign(lower, "r1", "1700000000"))
                .header("x-request-id", "r1").queryParam("data.id", "ORDOUTRO").contentType(ContentType.JSON)
                .body(body).when().post(URL).then().statusCode(401);
        given().header("x-signature", "ts=1700000001,v1=" + sign(lower, "r1", "1700000000"))
                .header("x-request-id", "r1").queryParam("data.id", orderId).contentType(ContentType.JSON)
                .body(body).when().post(URL).then().statusCode(401);
        given().header("x-request-id", "r1").queryParam("data.id", orderId).contentType(ContentType.JSON).body(body)
                .when().post(URL).then().statusCode(401);
        given().header("x-signature", "ts=1700000000,v1=" + sign(lower, "r1", "1700000000"))
                .queryParam("data.id", orderId).contentType(ContentType.JSON).body(body)
                .when().post(URL).then().statusCode(401);
        given().header("x-signature", "lixo").header("x-request-id", "r1").queryParam("data.id", orderId)
                .contentType(ContentType.JSON).body(body).when().post(URL).then().statusCode(401);
        given().header("x-signature", "ts=1700000000,v1=" + sign(lower, "r1", "1700000000"))
                .header("x-request-id", "r1").contentType(ContentType.JSON).body(body)
                .when().post(URL).then().statusCode(401);

        verify(gateway, never()).getOrder(any(), anyString());
        assertEquals(0, events(eventId));
        check(bookingId, b -> assertEquals(BookingStatus.HELD, b.getStatus()), p -> {
        });
    }

    @Test
    void signatureOverTheIdExactlyAsReceived_isAlsoAccepted() {
        String orderId = newOrderId();
        long bookingId = booking(orderId, BookingStatus.HELD, PaymentStatus.PENDING);
        providerSays(orderId, PaymentStatus.APPROVED);
        long eventId = EVENT_IDS.incrementAndGet();

        given().header("x-signature", "ts=1700000000,v1=" + sign(orderId, "r2", "1700000000"))
                .header("x-request-id", "r2").queryParam("data.id", orderId).contentType(ContentType.JSON)
                .body("{\"id\":%d}".formatted(eventId))
                .when().post(URL).then().statusCode(200);

        check(bookingId, b -> assertEquals(BookingStatus.CONFIRMED, b.getStatus()), p -> {
        });
    }

    @Test
    void notificationForAnUnknownOrder_isAcknowledgedAndIgnored() {
        signed(newOrderId(), EVENT_IDS.incrementAndGet()).when().post(URL).then().statusCode(200);

        verify(gateway, never()).getOrder(any(), anyString());
    }

    @Test
    void providerFailure_returns503WithoutRecordingTheEvent_soTheRetryIsProcessed() {
        String orderId = newOrderId();
        long bookingId = booking(orderId, BookingStatus.HELD, PaymentStatus.PENDING);
        long eventId = EVENT_IDS.incrementAndGet();
        doThrow(new PaymentProviderUnavailableException("fora do ar", null))
                .when(gateway).getOrder(CREDENTIALS, orderId);

        signed(orderId, eventId).when().post(URL).then().statusCode(503);

        assertEquals(0, events(eventId));
        check(bookingId, b -> assertEquals(BookingStatus.HELD, b.getStatus()), p -> {
        });
        providerSays(orderId, PaymentStatus.APPROVED);
        signed(orderId, eventId).when().post(URL).then().statusCode(200);
        check(bookingId, b -> assertEquals(BookingStatus.CONFIRMED, b.getStatus()), p -> {
        });
    }

    @Test
    void providerSayingExpired_releasesTheHeldBooking() {
        String orderId = newOrderId();
        long bookingId = booking(orderId, BookingStatus.HELD, PaymentStatus.PENDING);
        providerSays(orderId, PaymentStatus.EXPIRED);

        signed(orderId, EVENT_IDS.incrementAndGet()).when().post(URL).then().statusCode(200);

        check(bookingId, b -> assertEquals(BookingStatus.EXPIRED, b.getStatus()),
                p -> assertEquals(PaymentStatus.EXPIRED, p.getStatus()));
    }

    @Test
    void providerSayingRefunded_marksTheRefundDone() {
        String orderId = newOrderId();
        long bookingId = booking(orderId, BookingStatus.CANCELLED, PaymentStatus.APPROVED);
        providerSays(orderId, PaymentStatus.REFUNDED);

        signed(orderId, EVENT_IDS.incrementAndGet()).when().post(URL).then().statusCode(200);

        check(bookingId, b -> {
            assertEquals(RefundStatus.DONE, b.getRefundStatus());
            assertEquals(9000L, b.getRefundAmountCents());
        }, p -> assertEquals(PaymentStatus.REFUNDED, p.getStatus()));
    }

    @Test
    void providerSayingPending_changesNothing() {
        String orderId = newOrderId();
        long bookingId = booking(orderId, BookingStatus.HELD, PaymentStatus.PENDING);
        providerSays(orderId, PaymentStatus.PENDING);

        signed(orderId, EVENT_IDS.incrementAndGet()).when().post(URL).then().statusCode(200);

        check(bookingId, b -> assertEquals(BookingStatus.HELD, b.getStatus()),
                p -> assertEquals(PaymentStatus.PENDING, p.getStatus()));
    }
}
