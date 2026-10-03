package br.com.stringtracker.resource.admin;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubAdmin;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.ClubAdminRepository;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.repository.UserRepository;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ClubAdminResourceTest {

    private static final String ADMIN_A = "kc-clubadmin-a";
    private static final String ADMIN_B = "kc-clubadmin-b";

    @Inject
    UserRepository userRepository;

    @Inject
    ClubRepository clubRepository;

    @Inject
    ClubAdminRepository clubAdminRepository;

    @Inject
    MockMailbox mailbox;

    private long clubAId;
    private long clubBId;
    private String clubAName;

    @BeforeEach
    void seed() {
        mailbox.clear();
        QuarkusTransaction.requiringNew().run(() -> {
            User adminA = user(ADMIN_A);
            User adminB = user(ADMIN_B);
            clubAName = "Perfil Clube A " + UUID.randomUUID();
            Club clubA = Club.create(clubAName);
            Club clubB = Club.create("Perfil Clube B " + UUID.randomUUID());
            clubRepository.persist(clubA);
            clubRepository.persist(clubB);
            clubAdminRepository.persist(ClubAdmin.create(clubA, adminA));
            clubAdminRepository.persist(ClubAdmin.create(clubB, adminB));
            clubAId = clubA.getId();
            clubBId = clubB.getId();
        });
    }

    private User user(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId).orElseGet(() -> {
            User user = new User();
            user.setKeycloakId(keycloakId);
            user.setName(keycloakId);
            user.setEmail(keycloakId + "@example.com");
            userRepository.persist(user);
            return user;
        });
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void admin_editsTheirOwnClubProfile_andTheChangePersists() {
        String newName = "Clube Renomeado " + UUID.randomUUID();

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"%s","address":"Rua das Quadras, 10","whatsapp":"(98) 99999-1234"}
                        """.formatted(newName))
                .when().put("/api/admin/clubs/%d/profile".formatted(clubAId))
                .then().statusCode(200)
                .body("id", equalTo((int) clubAId))
                .body("name", equalTo(newName))
                .body("address", equalTo("Rua das Quadras, 10"))
                .body("whatsapp", equalTo("98999991234"))
                .body("paymentStatus", equalTo("NOT_CONNECTED"));

        given()
                .when().get("/api/admin/clubs/%d/profile".formatted(clubAId))
                .then().statusCode(200)
                .body("name", equalTo(newName))
                .body("address", equalTo("Rua das Quadras, 10"))
                .body("whatsapp", equalTo("98999991234"))
                .body("logoUrl", nullValue());
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void blankAddressAndWhatsapp_clearTheFields() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"%s\",\"address\":\"  \",\"whatsapp\":\"\"}".formatted(clubAName))
                .when().put("/api/admin/clubs/%d/profile".formatted(clubAId))
                .then().statusCode(200)
                .body("address", nullValue())
                .body("whatsapp", nullValue());
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void adminOfAnotherClub_isDeniedEverywhere() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Invasão\"}")
                .when().put("/api/admin/clubs/%d/profile".formatted(clubBId))
                .then().statusCode(403);

        given().when().get("/api/admin/clubs/%d/profile".formatted(clubBId)).then().statusCode(403);

        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"alguem@example.com\"}")
                .when().post("/api/admin/clubs/%d/admin-invites".formatted(clubBId))
                .then().statusCode(403);

        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"alguem@example.com\"}")
                .when().post("/api/admin/clubs/%d/coach-invites".formatted(clubBId))
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void invalidWhatsapp_returns400() {
        for (String whatsapp : new String[]{"12345", "98 9999-ABCD", "1234567890123456"}) {
            given()
                    .contentType(ContentType.JSON)
                    .body("{\"name\":\"%s\",\"whatsapp\":\"%s\"}".formatted(clubAName, whatsapp))
                    .when().put("/api/admin/clubs/%d/profile".formatted(clubAId))
                    .then().statusCode(400)
                    .body(equalTo("WhatsApp inválido"));
        }
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void oversizedOrBlankFields_return400() {
        String tooLongName = "N".repeat(121);
        String tooLongAddress = "A".repeat(256);
        String tooLongWhatsapp = "9".repeat(31);

        for (String body : new String[]{
                "{\"name\":\"%s\"}".formatted(tooLongName),
                "{\"name\":\"%s\",\"address\":\"%s\"}".formatted(clubAName, tooLongAddress),
                "{\"name\":\"%s\",\"whatsapp\":\"%s\"}".formatted(clubAName, tooLongWhatsapp),
                "{\"name\":\"   \"}"
        }) {
            given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when().put("/api/admin/clubs/%d/profile".formatted(clubAId))
                    .then().statusCode(400);
        }
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void renamingToAnotherClubsName_returns400() {
        String otherName = QuarkusTransaction.requiringNew().call(() -> clubRepository.findById(clubBId).getName());

        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"%s\"}".formatted(otherName.toLowerCase()))
                .when().put("/api/admin/clubs/%d/profile".formatted(clubAId))
                .then().statusCode(400)
                .body(equalTo("Já existe um clube com esse nome"));
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void admin_invitesAnotherAdminToTheirClub() {
        String email = "novo.admin." + UUID.randomUUID() + "@example.com";

        String link = given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"%s\"}".formatted(email))
                .when().post("/api/admin/clubs/%d/admin-invites".formatted(clubAId))
                .then().statusCode(201)
                .body("kind", equalTo("CLUB_ADMIN"))
                .body("email", equalTo(email))
                .extract().path("link");

        assertEquals(1, mailbox.getMailsSentTo(email).size());
        assertTrue(mailbox.getMailsSentTo(email).get(0).getText().contains(link));
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void admin_invitesACoachToTheirClub() {
        String email = "novo.professor." + UUID.randomUUID() + "@example.com";

        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"%s\"}".formatted(email))
                .when().post("/api/admin/clubs/%d/coach-invites".formatted(clubAId))
                .then().statusCode(201)
                .body("kind", equalTo("COACH"))
                .body("email", equalTo(email));

        assertEquals(1, mailbox.getMailsSentTo(email).size());
    }

    @Test
    @TestSecurity(user = "kc-clubadmin-common")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "kc-clubadmin-common")})
    void commonUser_isDenied() {
        given().when().get("/api/admin/clubs/%d/profile".formatted(clubAId)).then().statusCode(403);
    }
}
