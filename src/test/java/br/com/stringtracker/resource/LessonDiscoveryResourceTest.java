package br.com.stringtracker.resource;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubCoach;
import br.com.stringtracker.model.ClubPhoto;
import br.com.stringtracker.model.Coach;
import br.com.stringtracker.model.schedule.BookingStatus;
import br.com.stringtracker.model.schedule.LessonSlot;
import br.com.stringtracker.model.schedule.LessonSlotStatus;
import br.com.stringtracker.model.schedule.PaymentStatus;
import br.com.stringtracker.repository.CityRepository;
import br.com.stringtracker.repository.ClubPhotoRepository;
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
import java.util.concurrent.atomic.AtomicLong;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class LessonDiscoveryResourceTest {

    private static final String STUDENT = "kc-discover-student";
    private static final String NO_CITY = "kc-discover-no-city";
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    /** Cada teste usa a própria cidade semeada, para os clubes dos outros testes não entrarem na lista. */
    private static final AtomicLong NEXT_CITY = new AtomicLong(2000);

    @Inject
    ScheduleFixtures fixtures;

    @Inject
    CityRepository cityRepository;

    @Inject
    ClubPhotoRepository photoRepository;

    private long city;
    private long otherCity;

    @BeforeEach
    void seed() {
        QuarkusMock.installMockForType(Clock.fixed(NOW, ClockProducer.ZONE), Clock.class);
        city = NEXT_CITY.getAndAdd(2);
        otherCity = city + 1;
        QuarkusTransaction.requiringNew().run(() -> {
            fixtures.user(STUDENT).setCity(cityRepository.findById(city));
            fixtures.user(NO_CITY);
        });
    }

    private Club club(String prefix, long inCity) {
        return fixtures.club(prefix, inCity);
    }

    private ClubCoach link(Club club) {
        return fixtures.link(club, fixtures.coach(), 9000L, 12000L);
    }

    private LessonSlot slotIn(ClubCoach link, Duration fromNow) {
        return fixtures.slot(link, NOW.plus(fromNow));
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void listsOnlyActiveClubsOfTheCityWithAFreeBookableSlot_byNameWithTheNextFreeStart() {
        // Só o Alfa e o Zeta entram: cada um dos outros clubes falha numa condição diferente.
        long[] listed = QuarkusTransaction.requiringNew().call(() -> {
            Club a = club("Alfa", city);
            slotIn(link(a), Duration.ofDays(1));
            slotIn(link(a), Duration.ofHours(3));
            Club z = club("Zeta", city);
            slotIn(link(z), Duration.ofHours(30));
            slotIn(link(club("Alfa Outra Cidade", otherCity)), Duration.ofDays(1));
            link(club("Beta Sem Horario", city));
            Club off = club("Inativo", city);
            slotIn(link(off), Duration.ofDays(1));
            off.setActive(false);
            slotIn(link(club("Cedo Demais", city)), Duration.ofMinutes(90));
            Club lotado = club("Lotado", city);
            ClubCoach lotadoLink = link(lotado);
            fixtures.studentBooking(slotIn(lotadoLink, Duration.ofDays(1)), BookingStatus.CONFIRMED,
                    fixtures.user(STUDENT));
            fixtures.pixBooking(slotIn(lotadoLink, Duration.ofDays(2)), fixtures.user(NO_CITY), BookingStatus.HELD,
                    PaymentStatus.PENDING, "ORD-" + UUID.randomUUID());
            slotIn(link(club("Bloqueado", city)), Duration.ofDays(1)).setStatus(LessonSlotStatus.BLOCKED);
            slotIn(link(club("Removido", city)), Duration.ofDays(1)).setStatus(LessonSlotStatus.REMOVED);
            ClubCoach gone = link(club("Sem Vinculo", city));
            slotIn(gone, Duration.ofDays(1));
            gone.setActive(false);
            return new long[]{a.getId(), z.getId()};
        });

        given().queryParam("cityId", city).when().get("/api/lessons/clubs")
                .then().statusCode(200)
                .body("id", contains((int) listed[0], (int) listed[1]))
                .body("[0].nextFreeAt", equalTo("2026-10-05T15:00:00Z"))
                .body("[1].nextFreeAt", equalTo("2026-10-06T18:00:00Z"));
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void withoutCityIdItUsesTheStudentsProfileCity_andFailsWhenThereIsNone() {
        long clubId = QuarkusTransaction.requiringNew().call(() -> {
            Club club = club("Perfil", city);
            slotIn(link(club), Duration.ofDays(1));
            return club.getId();
        });

        given().when().get("/api/lessons/clubs").then().statusCode(200).body("id", contains((int) clubId));
        given().queryParam("cityId", otherCity).when().get("/api/lessons/clubs")
                .then().statusCode(200).body("$", empty());
    }

    @Test
    @TestSecurity(user = NO_CITY)
    @JwtSecurity(claims = {@Claim(key = "sub", value = NO_CITY)})
    void withoutCityIdAndWithoutAProfileCity_returns400() {
        given().when().get("/api/lessons/clubs").then().statusCode(400).body(equalTo("Informe a cidade"));
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void clubPage_showsIdentityPhotosPixFlagAndEachCoachWithTheBookableTypesAndNextFiveSlots() {
        long clubId = QuarkusTransaction.requiringNew().call(() -> {
            Club club = club("Pagina", city);
            club.setAddress("Rua das Quadras, 10");
            club.setWhatsapp("98999990000");
            club.setLogoUrl("/api/media/clubs/1/logo.png");
            fixtures.connectPayments(club, NOW.plus(Duration.ofDays(30)));
            photoRepository.persist(ClubPhoto.create(club, "/api/media/clubs/1/photos/b.png", 1));
            photoRepository.persist(ClubPhoto.create(club, "/api/media/clubs/1/photos/a.png", 0));

            ClubCoach main = link(club);
            slotIn(main, Duration.ofMinutes(90));
            for (int i = 0; i < 7; i++) {
                slotIn(main, Duration.ofDays(1).plusHours(2L * i));
            }
            Coach singlesOnly = fixtures.coach();
            singlesOnly.setOffersDoubles(false);
            singlesOnly.setOffersGroup(true);
            ClubCoach singlesLink = fixtures.link(club, singlesOnly, 8000L, 11000L);
            singlesLink.setPriceGroupCents(5000L);
            ClubCoach gone = link(club);
            gone.setActive(false);
            return club.getId();
        });

        given().when().get("/api/lessons/clubs/" + clubId)
                .then().statusCode(200)
                .body("name", org.hamcrest.Matchers.startsWith("Pagina"))
                .body("address", equalTo("Rua das Quadras, 10"))
                .body("whatsapp", equalTo("98999990000"))
                .body("logoUrl", equalTo("/api/media/clubs/1/logo.png"))
                .body("acceptsPix", equalTo(true))
                .body("photos.url", contains("/api/media/clubs/1/photos/a.png", "/api/media/clubs/1/photos/b.png"))
                .body("coaches", hasSize(2))
                .body("coaches.find { it.types.size() == 2 }.types.type", contains("SINGLES", "DOUBLES"))
                .body("coaches.find { it.types.size() == 2 }.types.priceCents", contains(9000, 12000))
                .body("coaches.find { it.types.size() == 2 }.nextSlots", hasSize(5))
                .body("coaches.find { it.types.size() == 2 }.nextSlots[0].startsAt", equalTo("2026-10-06T12:00:00Z"))
                .body("coaches.find { it.types.size() == 1 }.types.type", contains("SINGLES"))
                .body("coaches.find { it.types.size() == 1 }.types.priceCents", contains(8000))
                .body("coaches.find { it.types.size() == 1 }.nextSlots", empty());
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void clubWithoutAConnectedAccount_isStillShownWithItsSlotsButWithoutPix() {
        long clubId = QuarkusTransaction.requiringNew().call(() -> {
            Club club = club("Sem Pix", city);
            slotIn(link(club), Duration.ofDays(1));
            return club.getId();
        });

        given().when().get("/api/lessons/clubs/" + clubId)
                .then().statusCode(200)
                .body("acceptsPix", equalTo(false))
                .body("coaches[0].nextSlots", hasSize(1));
        given().queryParam("cityId", city).when().get("/api/lessons/clubs")
                .then().statusCode(200).body("[0].acceptsPix", equalTo(false));
    }

    @Test
    @TestSecurity(user = STUDENT)
    @JwtSecurity(claims = {@Claim(key = "sub", value = STUDENT)})
    void inactiveOrUnknownClub_returns404WithTheLinkUnavailableCode() {
        long inactive = QuarkusTransaction.requiringNew().call(() -> {
            Club club = club("Desativado", city);
            club.setActive(false);
            return club.getId();
        });

        for (long clubId : new long[]{inactive, 999999999L}) {
            given().when().get("/api/lessons/clubs/" + clubId)
                    .then().statusCode(404)
                    .body("code", equalTo("LINK_UNAVAILABLE"))
                    .body("message", equalTo("Este link não está mais disponível"));
        }
    }

    @Test
    void withoutLogin_returns401() {
        given().when().get("/api/lessons/clubs?cityId=" + city).then().statusCode(401);
        given().when().get("/api/lessons/clubs/1").then().statusCode(401);
    }
}
