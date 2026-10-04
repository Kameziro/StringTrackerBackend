package br.com.stringtracker.resource;

import br.com.stringtracker.client.MercadoPagoClient;
import br.com.stringtracker.client.MercadoPagoClient.CreateOrderRequest;
import br.com.stringtracker.client.MercadoPagoClient.Order;
import br.com.stringtracker.client.MercadoPagoClient.OrderPayment;
import br.com.stringtracker.client.MercadoPagoClient.OrderPaymentMethod;
import br.com.stringtracker.client.MercadoPagoClient.OrderTransactions;
import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.ClubPaymentStatus;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonSlotStatus;
import br.com.stringtracker.model.schedule.Payment;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.LessonSlotRepository;
import br.com.stringtracker.repository.PaymentRepository;
import br.com.stringtracker.service.ClockProducer;
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
import jakarta.ws.rs.ProcessingException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
class LessonBookingResourceTest {

    private static final String STUDENT = "kc-booking-student";
    private static final String OTHER = "kc-booking-other";
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    @InjectMock
    @RestClient
    MercadoPagoClient mercadoPago;

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    LessonSlotRepository lessonSlotRepository;

    private long slotId;

    /** Clube com Pix conectado; professor com singles (R$ 90) e duplas (R$ 120), sem grupo; um horário amanhã. */
    @BeforeEach
    void seed() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
        when(mercadoPago.createOrder(anyString(), anyString(), any()))
                .thenAnswer(call -> pixOrder("ORD-" + UUID.randomUUID()));
        QuarkusTransaction.requiringNew().run(() -> {
            fixtures.user(STUDENT);
            fixtures.user(OTHER);
            Club club = fixtures.connectPayments(fixtures.club("Reserva Clube"), NOW.plus(Duration.ofDays(30)));
            Coach coach = fixtures.coach();
            ClubCoach link = fixtures.link(club, coach, 9000L, 12000L);
            LessonSlot slot = fixtures.slot(link, NOW.plus(Duration.ofDays(1)));
            slotId = slot.getId();
        });
    }

    private static Order pixOrder(String id) {
        return new Order(id, "action_required", "waiting_transfer", new OrderTransactions(List.of(new OrderPayment(
                "PAY", "action_required", new OrderPaymentMethod("copia-e-cola", "qr-base64", "https://mp/ticket")))));
    }

    private static String hold(long slot, String type, String partner) {
        return "{\"slotId\":%d,\"type\":\"%s\"%s}".formatted(slot, type,
                partner == null ? "" : ",\"partnerName\":\"" + partner + "\"");
    }

    private record BookingRow(long id, BookingStatus status, long priceCents, int seat, String partnerName,
                              Instant holdExpiresAt, String student) {
    }

    private List<BookingRow> bookingsOf(long slot) {
        return QuarkusTransaction.requiringNew().call(() -> bookingRepository.list("lessonSlot.id", slot).stream()
                .map(b -> new BookingRow(b.getId(), b.getStatus(), b.getPriceCents(), b.getSeat(), b.getPartnerName(),
                        b.getHoldExpiresAt(), b.getStudentUser().getKeycloakId()))
                .toList());
    }

    private Payment paymentOf(long bookingId) {
        return QuarkusTransaction.requiringNew().call(() -> paymentRepository.find("booking.id", bookingId).firstResult());
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void singles_holdsTheSlotForTenMinutesAndReturnsThePixAtTheSinglesPrice() {
        given().contentType(ContentType.JSON).body(hold(slotId, "SINGLES", null))
                .when().post("/api/lessons/bookings")
                .then().statusCode(201)
                .body("status", equalTo("HELD"))
                .body("priceCents", equalTo(9000))
                .body("lessonType", equalTo("SINGLES"))
                .body("holdExpiresAt", equalTo("2026-10-05T12:10:00Z"))
                .body("pix.copiaECola", equalTo("copia-e-cola"))
                .body("pix.qrCodeBase64", equalTo("qr-base64"))
                .body("pix.expiresAt", equalTo("2026-10-05T12:10:00Z"));

        BookingRow booking = bookingsOf(slotId).get(0);
        assertEquals(BookingStatus.HELD, booking.status());
        assertEquals(9000L, booking.priceCents());
        assertEquals(1, booking.seat());
        assertEquals(STUDENT, booking.student());
        assertEquals(NOW.plus(Duration.ofMinutes(10)), booking.holdExpiresAt());
        Payment payment = paymentOf(booking.id());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals(9000L, payment.getAmountCents());
        assertTrue(payment.getProviderPaymentId().startsWith("ORD-"));
        assertEquals(booking.holdExpiresAt(), payment.getExpiresAt());

        ArgumentCaptor<CreateOrderRequest> request = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(mercadoPago).createOrder(eq("Bearer club-access-token"), eq("booking-" + booking.id()),
                request.capture());
        assertEquals("90.00", request.getValue().totalAmount());
        assertEquals("PT10M", request.getValue().transactions().payments().get(0).expirationTime());
        assertEquals(STUDENT + "@example.com", request.getValue().payer().email());
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void doubles_chargesTheDoublesPriceAndKeepsThePartnerName() {
        given().contentType(ContentType.JSON).body(hold(slotId, "DOUBLES", "Maria"))
                .when().post("/api/lessons/bookings")
                .then().statusCode(201)
                .body("priceCents", equalTo(12000))
                .body("partnerName", equalTo("Maria"));

        ArgumentCaptor<CreateOrderRequest> request = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(mercadoPago).createOrder(anyString(), anyString(), request.capture());
        assertEquals("120.00", request.getValue().totalAmount());
        assertEquals("Maria", bookingsOf(slotId).get(0).partnerName());
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void typeTheCoachDoesNotOfferOrDidNotPrice_returns422AndHoldsNothing() {
        QuarkusTransaction.requiringNew().run(() ->
                lessonSlotRepository.findById(slotId).getCoach().setOffersDoubles(false));
        // Grupo não é oferecido; duplas foi desligada; singles perde o preço no terceiro caso.
        for (String type : new String[]{"GROUP", "DOUBLES"}) {
            given().contentType(ContentType.JSON).body(hold(slotId, type, null))
                    .when().post("/api/lessons/bookings")
                    .then().statusCode(422)
                    .body(equalTo("O professor não oferece esse tipo de aula neste horário"));
        }
        QuarkusTransaction.requiringNew().run(() -> {
            ClubCoach link = lessonSlotRepository.findById(slotId).getClubCoach();
            link.setPriceSinglesCents(null);
        });
        given().contentType(ContentType.JSON).body(hold(slotId, "SINGLES", null))
                .when().post("/api/lessons/bookings")
                .then().statusCode(422)
                .body(equalTo("O professor não tem preço definido para esse tipo de aula"));

        assertTrue(bookingsOf(slotId).isEmpty());
        verify(mercadoPago, never()).createOrder(anyString(), anyString(), any());
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void lessThanTwoHoursBeforeStart_returns422_exactlyTwoHoursIsStillAllowed() {
        long tooLate = QuarkusTransaction.requiringNew().call(() -> fixtures.slot(
                lessonSlotRepository.findById(slotId).getClubCoach(), NOW.plus(Duration.ofMinutes(119))).getId());
        // Outro professor: o mesmo professor não pode ter dois horários sobrepostos.
        long exactly = QuarkusTransaction.requiringNew().call(() -> {
            Club club = lessonSlotRepository.findById(slotId).getClubCoach().getClub();
            ClubCoach other = fixtures.link(club, fixtures.coach(), 9000L, null);
            return fixtures.slot(other, NOW.plus(Duration.ofHours(2))).getId();
        });

        given().contentType(ContentType.JSON).body(hold(tooLate, "SINGLES", null))
                .when().post("/api/lessons/bookings")
                .then().statusCode(422)
                .body(equalTo("Reservas fecham 2h antes da aula"));
        given().contentType(ContentType.JSON).body(hold(exactly, "SINGLES", null))
                .when().post("/api/lessons/bookings")
                .then().statusCode(201);

        assertTrue(bookingsOf(tooLate).isEmpty());
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void invalidRequests_return400() {
        for (String body : new String[]{
                hold(slotId, "DOUBLES", "x".repeat(121)),
                "{\"type\":\"SINGLES\"}",
                "{\"slotId\":%d}".formatted(slotId),
                "{\"slotId\":%d,\"type\":\"TRIPLES\"}".formatted(slotId)
        }) {
            given().contentType(ContentType.JSON).body(body)
                    .when().post("/api/lessons/bookings")
                    .then().statusCode(400);
        }

        assertTrue(bookingsOf(slotId).isEmpty());
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void twoSimultaneousRequestsForTheSameSlot_oneGets201AndTheOtherGets409() throws Exception {
        when(mercadoPago.createOrder(anyString(), anyString(), any())).thenAnswer(call -> {
            Thread.sleep(400);
            return pixOrder("ORD-" + UUID.randomUUID());
        });
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> statuses = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        try {
            List<Future<io.restassured.response.Response>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    return given().contentType(ContentType.JSON).body(hold(slotId, "SINGLES", null))
                            .when().post("/api/lessons/bookings");
                }));
            }
            go.countDown();
            for (Future<io.restassured.response.Response> result : results) {
                io.restassured.response.Response response = result.get();
                statuses.add(response.statusCode());
                messages.add(response.asString());
            }
        } finally {
            pool.shutdownNow();
        }

        assertTrue(statuses.contains(201) && statuses.contains(409), "statuses: " + statuses);
        assertTrue(messages.contains("Esse horário acabou de ser reservado"), "messages: " + messages);
        assertEquals(1, bookingsOf(slotId).size());
        verify(mercadoPago, times(1)).createOrder(anyString(), anyString(), any());
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void slotAlreadyHeldOrConfirmed_returns409_butAfterACancellationOrExpiryItIsFreeAgain() {
        QuarkusTransaction.requiringNew().run(() -> fixtures.studentBooking(
                lessonSlotRepository.findById(slotId), BookingStatus.CONFIRMED, fixtures.user(OTHER)));
        given().contentType(ContentType.JSON).body(hold(slotId, "SINGLES", null))
                .when().post("/api/lessons/bookings")
                .then().statusCode(409)
                .body(equalTo("Esse horário acabou de ser reservado"));

        QuarkusTransaction.requiringNew().run(() ->
                bookingRepository.update("status = ?1", BookingStatus.CANCELLED));
        given().contentType(ContentType.JSON).body(hold(slotId, "SINGLES", null))
                .when().post("/api/lessons/bookings")
                .then().statusCode(201);
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void blockedSlot_returns409_removedOrUnknownSlot_returns404() {
        long blockedId = QuarkusTransaction.requiringNew().call(() -> {
            LessonSlot slot = fixtures.slot(lessonSlotRepository.findById(slotId).getClubCoach(),
                    NOW.plus(Duration.ofDays(2)));
            slot.setStatus(LessonSlotStatus.BLOCKED);
            return slot.getId();
        });
        long removedId = QuarkusTransaction.requiringNew().call(() -> {
            LessonSlot slot = fixtures.slot(lessonSlotRepository.findById(slotId).getClubCoach(),
                    NOW.plus(Duration.ofDays(3)));
            slot.setStatus(LessonSlotStatus.REMOVED);
            return slot.getId();
        });

        given().contentType(ContentType.JSON).body(hold(blockedId, "SINGLES", null))
                .when().post("/api/lessons/bookings").then().statusCode(409);
        given().contentType(ContentType.JSON).body(hold(removedId, "SINGLES", null))
                .when().post("/api/lessons/bookings").then().statusCode(404);
        given().contentType(ContentType.JSON).body(hold(999999999L, "SINGLES", null))
                .when().post("/api/lessons/bookings").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void providerDown_returns503AndLeavesNoHeldBookingNorPayment() {
        when(mercadoPago.createOrder(anyString(), anyString(), any())).thenThrow(new ProcessingException("timeout"));

        given().contentType(ContentType.JSON).body(hold(slotId, "SINGLES", null))
                .when().post("/api/lessons/bookings")
                .then().statusCode(503)
                .body(equalTo("Não foi possível gerar o Pix. Tente novamente"));

        assertTrue(bookingsOf(slotId).isEmpty());
        assertEquals(0, paymentRepository.count("booking.lessonSlot.id", slotId));
        // O horário continua livre para a próxima tentativa.
        doReturn(pixOrder("ORD-RETRY")).when(mercadoPago).createOrder(anyString(), anyString(), any());
        given().contentType(ContentType.JSON).body(hold(slotId, "SINGLES", null))
                .when().post("/api/lessons/bookings").then().statusCode(201);
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void clubWithoutAConnectedAccount_returns422AndDoesNotCallTheProvider() {
        QuarkusTransaction.requiringNew().run(() -> {
            Club club = lessonSlotRepository.findById(slotId).getClubCoach().getClub();
            club.setPaymentStatus(ClubPaymentStatus.NOT_CONNECTED);
        });

        given().contentType(ContentType.JSON).body(hold(slotId, "SINGLES", null))
                .when().post("/api/lessons/bookings")
                .then().statusCode(422)
                .body(equalTo("O clube não tem conta de recebimento conectada"));

        assertTrue(bookingsOf(slotId).isEmpty());
        verify(mercadoPago, never()).createOrder(anyString(), anyString(), any());
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void studentReadsTheirBookingStatus_butNotSomeoneElses() {
        long bookingId = given().contentType(ContentType.JSON).body(hold(slotId, "SINGLES", null))
                .when().post("/api/lessons/bookings")
                .then().statusCode(201).extract().jsonPath().getLong("bookingId");

        given().when().get("/api/lessons/bookings/" + bookingId)
                .then().statusCode(200)
                .body("bookingId", equalTo((int) bookingId))
                .body("status", equalTo("HELD"))
                .body("holdExpiresAt", notNullValue());
        given().when().get("/api/lessons/bookings/999999999").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void heldBooking_returnsTheSamePixAsTheCreation_withoutCallingTheProvider() {
        var created = given().contentType(ContentType.JSON).body(hold(slotId, "SINGLES", null))
                .when().post("/api/lessons/bookings")
                .then().statusCode(201).extract().jsonPath();

        given().when().get("/api/lessons/bookings/" + created.getLong("bookingId"))
                .then().statusCode(200)
                .body("pix.copiaECola", equalTo("copia-e-cola"))
                .body("pix.qrCodeBase64", equalTo("qr-base64"))
                .body("pix.ticketUrl", equalTo("https://mp/ticket"))
                .body("pix.expiresAt", equalTo("2026-10-05T12:10:00Z"))
                .body("pix", equalTo(created.getMap("pix")));
        verify(mercadoPago, times(1)).createOrder(anyString(), anyString(), any());
        verifyNoMoreInteractions(mercadoPago);
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void bookingThatIsNoLongerHeld_hasNoPix() {
        long bookingId = given().contentType(ContentType.JSON).body(hold(slotId, "SINGLES", null))
                .when().post("/api/lessons/bookings")
                .then().statusCode(201).extract().jsonPath().getLong("bookingId");

        for (BookingStatus status : new BookingStatus[]{BookingStatus.CONFIRMED, BookingStatus.CANCELLED,
                BookingStatus.EXPIRED}) {
            QuarkusTransaction.requiringNew().run(() -> bookingRepository.findById(bookingId).setStatus(status));
            given().when().get("/api/lessons/bookings/" + bookingId)
                    .then().statusCode(200)
                    .body("status", equalTo(status.name()))
                    .body("pix", nullValue());
        }
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void heldBookingWhosePixWasNotStored_hasNoPix() {
        long bookingId = QuarkusTransaction.requiringNew().call(() -> fixtures.pixBooking(
                lessonSlotRepository.findById(slotId), fixtures.user(STUDENT), BookingStatus.HELD,
                PaymentStatus.PENDING, "ORD-" + UUID.randomUUID()).getId());

        given().when().get("/api/lessons/bookings/" + bookingId)
                .then().statusCode(200)
                .body("status", equalTo("HELD"))
                .body("pix", nullValue());
    }

    @Test
    @TestSecurity(user = OTHER)
    @JwtSecurity(claims = {@Claim(key = "sub", value = OTHER)})
    void anotherStudentCannotReadTheBooking_returns404() {
        long bookingId = QuarkusTransaction.requiringNew().call(() -> fixtures.studentBooking(
                lessonSlotRepository.findById(slotId), BookingStatus.HELD, fixtures.user(STUDENT)).getId());

        given().when().get("/api/lessons/bookings/" + bookingId).then().statusCode(404);
    }
}
