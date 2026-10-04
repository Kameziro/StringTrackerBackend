package br.com.stringtracker.resource.admin;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonKind;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonSlotStatus;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.model.schedule.ScheduleBlock;
import br.com.stringtracker.repository.BookingRepository;
import br.com.stringtracker.repository.ClubCoachRepository;
import br.com.stringtracker.repository.LessonSlotRepository;
import br.com.stringtracker.repository.PaymentRepository;
import br.com.stringtracker.repository.ScheduleBlockRepository;
import br.com.stringtracker.service.ClockProducer;
import br.com.stringtracker.service.ScheduleService;
import br.com.stringtracker.service.payment.PaymentGateway;
import br.com.stringtracker.service.payment.PaymentGateway.ClubCredentials;
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
import java.time.LocalTime;
import java.util.UUID;
import java.util.function.Function;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class CoachUnlinkResourceTest {

    private static final String ADMIN = "kc-unlink-admin";
    private static final String OTHER_ADMIN = "kc-unlink-other-admin";
    private static final String STUDENT = "kc-unlink-student";
    private static final String OTHER_STUDENT = "kc-unlink-other-student";
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final ClubCredentials CREDENTIALS = new ClubCredentials("club-token");

    @InjectMock
    PaymentGateway gateway;

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    BookingRepository bookingRepository;

    @Inject
    LessonSlotRepository lessonSlotRepository;

    @Inject
    ClubCoachRepository clubCoachRepository;

    @Inject
    ScheduleBlockRepository scheduleBlockRepository;

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    ScheduleService scheduleService;

    private long clubAId;
    private long coachId;
    private long linkAId;
    private long linkBId;
    private long blockAId;
    private long blockBId;
    private long confirmedAId;
    private long heldAId;
    private long pastAId;
    private long confirmedBId;
    private long freeSlotAId;
    private long slotBId;
    private long pastSlotAId;
    private String confirmedAOrderId;
    private String heldAOrderId;

    /**
     * O professor atende nos clubes A e B. No A: uma aula confirmada e paga (daqui a 2 dias), uma segurada, um horário
     * livre, uma aula já dada e um bloco semanal. No B: uma aula confirmada e um bloco. ADMIN administra só o A.
     */
    @BeforeEach
    void seed() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
        when(gateway.refreshIfNeeded(any(Club.class))).thenReturn(CREDENTIALS);
        QuarkusTransaction.requiringNew().run(() -> {
            Club clubA = fixtures.connectPayments(fixtures.club("Desvincula Clube A"), NOW.plus(Duration.ofDays(30)));
            Club clubB = fixtures.connectPayments(fixtures.club("Desvincula Clube B"), NOW.plus(Duration.ofDays(30)));
            fixtures.admin(clubA, fixtures.user(ADMIN));
            fixtures.admin(fixtures.club("Desvincula Outro"), fixtures.user(OTHER_ADMIN));
            fixtures.user(STUDENT);
            fixtures.user(OTHER_STUDENT);
            Coach coach = fixtures.coach();
            ClubCoach linkA = fixtures.link(clubA, coach, 9000L, 12000L);
            ClubCoach linkB = fixtures.link(clubB, coach, 9000L, 12000L);

            ScheduleBlock blockA = ScheduleBlock.create(linkA, LessonKind.PRIVATE, (short) 3, LocalTime.of(10, 0),
                    LocalTime.of(12, 0), (short) 60, (short) 1);
            ScheduleBlock blockB = ScheduleBlock.create(linkB, LessonKind.PRIVATE, (short) 4, LocalTime.of(10, 0),
                    LocalTime.of(12, 0), (short) 60, (short) 1);
            scheduleBlockRepository.persist(blockA);
            scheduleBlockRepository.persist(blockB);

            Instant day = NOW.plus(Duration.ofDays(2));
            confirmedAOrderId = "ORD-" + UUID.randomUUID();
            LessonSlot slotA = fixtures.slot(linkA, day);
            confirmedAId = fixtures.pixBooking(slotA, fixtures.user(STUDENT), BookingStatus.CONFIRMED,
                    PaymentStatus.APPROVED, confirmedAOrderId).getId();
            heldAOrderId = "ORD-" + UUID.randomUUID();
            heldAId = fixtures.pixBooking(fixtures.slot(linkA, day.plus(Duration.ofHours(2))),
                    fixtures.user(OTHER_STUDENT), BookingStatus.HELD, PaymentStatus.PENDING, heldAOrderId).getId();
            freeSlotAId = fixtures.slot(linkA, day.plus(Duration.ofHours(4))).getId();
            LessonSlot pastSlot = fixtures.slot(linkA, NOW.minus(Duration.ofDays(2)));
            pastAId = fixtures.studentBooking(pastSlot, BookingStatus.CONFIRMED, fixtures.user(STUDENT)).getId();
            LessonSlot slotB = fixtures.slot(linkB, day.plus(Duration.ofHours(6)));
            confirmedBId = fixtures.pixBooking(slotB, fixtures.user(OTHER_STUDENT), BookingStatus.CONFIRMED,
                    PaymentStatus.APPROVED, "ORD-" + UUID.randomUUID()).getId();

            clubAId = clubA.getId();
            coachId = coach.getId();
            linkAId = linkA.getId();
            linkBId = linkB.getId();
            blockAId = blockA.getId();
            blockBId = blockB.getId();
            slotBId = slotB.getId();
            pastSlotAId = pastSlot.getId();
        });
    }

    private String url(long club, long coach, boolean confirm) {
        return "/api/admin/clubs/" + club + "/coaches/" + coach + (confirm ? "?confirm=true" : "");
    }

    private <T> T inTx(Function<Void, T> read) {
        return QuarkusTransaction.requiringNew().call(() -> read.apply(null));
    }

    private boolean linkActive(long id) {
        return QuarkusTransaction.requiringNew().call(() -> clubCoachRepository.findById(id).isActive());
    }

    private boolean blockActive(long id) {
        return QuarkusTransaction.requiringNew().call(() -> scheduleBlockRepository.findById(id).isActive());
    }

    private BookingStatus bookingStatus(long id) {
        return inTx(v -> bookingRepository.findById(id).getStatus());
    }

    private LessonSlotStatus slotStatus(long id) {
        return inTx(v -> lessonSlotRepository.findById(id).getStatus());
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void withoutConfirmation_listsTheFutureLessonsOfTheClubAndChangesNothing() {
        given().when().delete(url(clubAId, coachId, false))
                .then().statusCode(200)
                .body("applied", equalTo(false))
                .body("affected.bookingId", containsInAnyOrder((int) confirmedAId, (int) heldAId))
                .body("affected.find { it.bookingId == " + confirmedAId + " }.studentName", equalTo(STUDENT))
                .body("affected.find { it.bookingId == " + heldAId + " }.status", equalTo("HELD"));

        assertTrue(linkActive(linkAId));
        assertEquals(BookingStatus.CONFIRMED, bookingStatus(confirmedAId));
        assertEquals(LessonSlotStatus.OPEN, slotStatus(freeSlotAId));
        verify(gateway, never()).refund(any(), anyString(), anyString());
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void withConfirmation_cancelsAndRefundsTheFutureLessonsRemovesTheSlotsAndDeactivatesTheLink() {
        given().when().delete(url(clubAId, coachId, true))
                .then().statusCode(200)
                .body("applied", equalTo(true))
                .body("affected.size()", equalTo(2))
                .body("affected.find { it.bookingId == " + confirmedAId + " }.refundStatus", equalTo("DONE"))
                .body("affected.find { it.bookingId == " + confirmedAId + " }.refundAmountCents", equalTo(9000))
                .body("affected.find { it.bookingId == " + heldAId + " }.refundStatus", equalTo("NONE"));

        verify(gateway).refund(CREDENTIALS, confirmedAOrderId, "refund-" + confirmedAId);
        verify(gateway, times(1)).refund(any(), anyString(), anyString());
        verify(gateway).cancel(CREDENTIALS, heldAOrderId, "cancel-" + heldAId);
        assertEquals(BookingStatus.CANCELLED, bookingStatus(confirmedAId));
        assertEquals(BookingStatus.CANCELLED, bookingStatus(heldAId));
        assertEquals(ADMIN, inTx(v -> bookingRepository.findById(confirmedAId).getCancelledBy().getKeycloakId()));
        assertEquals(LessonSlotStatus.REMOVED, slotStatus(freeSlotAId));
        assertFalse(linkActive(linkAId));
        assertFalse(blockActive(blockAId));
        assertEquals(PaymentStatus.EXPIRED, inTx(v -> paymentRepository.findByBookingId(heldAId).orElseThrow()
                .getStatus()));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void theLinkWithAnotherClubAndTheLessonsAlreadyGiven_areKept() {
        given().when().delete(url(clubAId, coachId, true)).then().statusCode(200);

        assertTrue(linkActive(linkBId));
        assertTrue(blockActive(blockBId));
        assertEquals(LessonSlotStatus.OPEN, slotStatus(slotBId));
        assertEquals(BookingStatus.CONFIRMED, bookingStatus(confirmedBId));
        assertEquals(LessonSlotStatus.OPEN, slotStatus(pastSlotAId));
        assertEquals(BookingStatus.CONFIRMED, bookingStatus(pastAId));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void afterUnlinking_theBlockStopsGeneratingSlotsAndTheCoachLeavesTheClubsAgenda() {
        given().when().delete(url(clubAId, coachId, true)).then().statusCode(200);

        assertEquals(0, scheduleService.extendBlock(blockAId));
        assertTrue(scheduleService.extendBlock(blockBId) > 0);
        given().when().get("/api/admin/clubs/" + clubAId + "/agenda?week=2026-10-05")
                .then().statusCode(200).body("coaches.size()", equalTo(0));
    }

    @Test
    @TestSecurity(user = ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN)})
    void unlinkingAgainOrAnUnknownCoach_returns404() {
        given().when().delete(url(clubAId, coachId, true)).then().statusCode(200);

        given().when().delete(url(clubAId, coachId, true)).then().statusCode(404);
        given().when().delete(url(clubAId, 999999999L, false)).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = OTHER_ADMIN)
    @JwtSecurity(claims = {@Claim(key = "sub", value = OTHER_ADMIN)})
    void adminOfAnotherClub_gets403AndNothingChanges() {
        given().when().delete(url(clubAId, coachId, true)).then().statusCode(403);
        given().when().delete(url(clubAId, coachId, false)).then().statusCode(403);

        assertTrue(linkActive(linkAId));
        assertEquals(BookingStatus.CONFIRMED, bookingStatus(confirmedAId));
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void userWhoIsNotAnAdmin_gets403() {
        given().when().delete(url(clubAId, coachId, true)).then().statusCode(403);

        assertTrue(linkActive(linkAId));
    }
}
