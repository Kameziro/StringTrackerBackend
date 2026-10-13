package br.com.stringtracker.resource.admin;

import br.com.stringtracker.model.Club;
import br.com.stringtracker.model.ClubAdmin;
import br.com.stringtracker.model.ClubPhoto;
import br.com.stringtracker.model.User;
import br.com.stringtracker.repository.ClubAdminRepository;
import br.com.stringtracker.repository.ClubPhotoRepository;
import br.com.stringtracker.repository.ClubRepository;
import br.com.stringtracker.repository.UserRepository;
import br.com.stringtracker.service.MinioObjectStorage;
import br.com.stringtracker.service.MinioObjectStorage.ClubImageKind;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class ClubImagesResourceTest {

    private static final String ADMIN_A = "kc-clubimg-a";
    private static final String ADMIN_B = "kc-clubimg-b";
    private static final int MAX_BYTES = 5 * 1024 * 1024;

    @Inject
    UserRepository userRepository;

    @Inject
    ClubRepository clubRepository;

    @Inject
    ClubAdminRepository clubAdminRepository;

    @Inject
    ClubPhotoRepository photoRepository;

    private long clubAId;
    private long clubBId;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            User adminA = user(ADMIN_A);
            User adminB = user(ADMIN_B);
            Club clubA = Club.create("Imagens Clube A " + UUID.randomUUID());
            Club clubB = Club.create("Imagens Clube B " + UUID.randomUUID());
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

    /** Storage sem MinIO: devolve uma URL de mídia por upload e converte URLs como o real. */
    private MinioObjectStorage installStorageMock() {
        MinioObjectStorage storage = Mockito.mock(MinioObjectStorage.class);
        when(storage.uploadClubImage(anyLong(), any(), any(), anyLong(), anyString()))
                .thenAnswer(call -> "/api/media/clubs/%d/%s.jpg".formatted(call.getArgument(0), UUID.randomUUID()));
        when(storage.toClientMediaUrl(anyString())).thenAnswer(call -> call.getArgument(0));
        when(storage.extractObjectKey(anyString()))
                .thenAnswer(call -> call.<String>getArgument(0).replace("/api/media/", ""));
        QuarkusMock.installMockForType(storage, MinioObjectStorage.class);
        return storage;
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void addPhoto_acceptsJpegPngAndWebp_andListsThemInOrder() {
        MinioObjectStorage storage = installStorageMock();
        String[][] files = {{"a.jpg", "image/jpeg"}, {"b.png", "image/png"}, {"c.webp", "image/webp"}};

        for (String[] file : files) {
            byte[] bytes = file[0].getBytes();
            given()
                    .multiPart("file", file[0], bytes, file[1])
                    .when().post("/api/admin/clubs/%d/photos".formatted(clubAId))
                    .then().statusCode(201)
                    .body("id", notNullValue())
                    .body("url", startsWith("/api/media/clubs/%d/".formatted(clubAId)));
            verify(storage).uploadClubImage(eq(clubAId), eq(ClubImageKind.PHOTO), any(), eq((long) bytes.length), eq(file[1]));
        }

        given()
                .when().get("/api/admin/clubs/%d/profile".formatted(clubAId))
                .then().statusCode(200)
                .body("photos", hasSize(3))
                .body("photos.position", contains(0, 1, 2));
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void addPhoto_withAnotherFormatOrOver5Mb_returns400AndStoresNothing() {
        given()
                .multiPart("file", "animada.gif", new byte[]{1, 2, 3}, "image/gif")
                .when().post("/api/admin/clubs/%d/photos".formatted(clubAId))
                .then().statusCode(400)
                .body(equalTo("Use JPEG, PNG ou WebP"));

        given()
                .multiPart("file", "grande.png", new byte[MAX_BYTES + 1], "image/png")
                .when().post("/api/admin/clubs/%d/photos".formatted(clubAId))
                .then().statusCode(400)
                .body(equalTo("A imagem deve ter no máximo 5 MB"));

        given()
                .when().get("/api/admin/clubs/%d/profile".formatted(clubAId))
                .then().statusCode(200)
                .body("photos", empty());
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void addPhoto_whenTheClubAlreadyHasTen_returns422WithoutUploading() {
        MinioObjectStorage storage = installStorageMock();
        QuarkusTransaction.requiringNew().run(() -> {
            Club club = clubRepository.findById(clubAId);
            for (int i = 0; i < 10; i++) {
                photoRepository.persist(ClubPhoto.create(club, "/api/media/clubs/%d/p%d.jpg".formatted(clubAId, i), i));
            }
        });

        given()
                .multiPart("file", "extra.jpg", new byte[]{1}, "image/jpeg")
                .when().post("/api/admin/clubs/%d/photos".formatted(clubAId))
                .then().statusCode(422)
                .body(equalTo("Limite de 10 fotos atingido"));

        verify(storage, never()).uploadClubImage(anyLong(), any(), any(), anyLong(), anyString());
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void removePhoto_dropsItFromTheGalleryAndDeletesTheObject() {
        MinioObjectStorage storage = installStorageMock();
        var created = given()
                .multiPart("file", "a.jpg", new byte[]{1}, "image/jpeg")
                .when().post("/api/admin/clubs/%d/photos".formatted(clubAId))
                .then().statusCode(201)
                .extract().jsonPath();

        given()
                .when().delete("/api/admin/clubs/%d/photos/%d".formatted(clubAId, created.getLong("id")))
                .then().statusCode(204);

        given()
                .when().get("/api/admin/clubs/%d/profile".formatted(clubAId))
                .then().statusCode(200)
                .body("photos", empty());
        verify(storage).deleteObjectIfPresent(created.getString("url"));
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void removePhoto_ofAnotherClubOrUnknown_returns404() {
        long foreignPhotoId = QuarkusTransaction.requiringNew().call(() -> {
            ClubPhoto photo = ClubPhoto.create(clubRepository.findById(clubBId), "/api/media/clubs/x.jpg", 0);
            photoRepository.persist(photo);
            return photo.getId();
        });

        given()
                .when().delete("/api/admin/clubs/%d/photos/%d".formatted(clubAId, foreignPhotoId))
                .then().statusCode(404);
        given()
                .when().delete("/api/admin/clubs/%d/photos/999999999".formatted(clubAId))
                .then().statusCode(404);
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void uploadLogo_setsTheLogoAndReplacesThePreviousObject() {
        MinioObjectStorage storage = installStorageMock();

        String first = given()
                .multiPart("file", "logo.jpg", new byte[]{1}, "image/jpeg")
                .when().post("/api/admin/clubs/%d/logo".formatted(clubAId))
                .then().statusCode(200)
                .body("logoUrl", startsWith("/api/media/clubs/%d/".formatted(clubAId)))
                .extract().path("logoUrl");

        String second = given()
                .multiPart("file", "logo2.jpg", new byte[]{2, 3}, "image/jpeg")
                .when().post("/api/admin/clubs/%d/logo".formatted(clubAId))
                .then().statusCode(200)
                .extract().path("logoUrl");

        verify(storage).uploadClubImage(eq(clubAId), eq(ClubImageKind.LOGO), any(), eq(1L), eq("image/jpeg"));
        verify(storage).uploadClubImage(eq(clubAId), eq(ClubImageKind.LOGO), any(), eq(2L), eq("image/jpeg"));
        verify(storage).deleteObjectIfPresent(first);
        given()
                .when().get("/api/admin/clubs/%d/profile".formatted(clubAId))
                .then().statusCode(200)
                .body("logoUrl", equalTo(second));
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void uploadLogo_withAnotherFormat_returns400AndKeepsNoLogo() {
        given()
                .multiPart("file", "logo.gif", new byte[]{1}, "image/gif")
                .when().post("/api/admin/clubs/%d/logo".formatted(clubAId))
                .then().statusCode(400);

        given()
                .when().get("/api/admin/clubs/%d/profile".formatted(clubAId))
                .then().statusCode(200)
                .body("logoUrl", nullValue());
    }

    @Test
    @TestSecurity(user = ADMIN_A)
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_A)})
    void adminOfAnotherClub_isDeniedOnPhotosAndLogo() {
        MinioObjectStorage storage = installStorageMock();

        given()
                .multiPart("file", "a.jpg", new byte[]{1}, "image/jpeg")
                .when().post("/api/admin/clubs/%d/photos".formatted(clubBId))
                .then().statusCode(403);
        given()
                .multiPart("file", "logo.jpg", new byte[]{1}, "image/jpeg")
                .when().post("/api/admin/clubs/%d/logo".formatted(clubBId))
                .then().statusCode(403);
        given()
                .when().delete("/api/admin/clubs/%d/photos/1".formatted(clubBId))
                .then().statusCode(403);

        verify(storage, never()).uploadClubImage(anyLong(), any(), any(), anyLong(), anyString());
    }
}
