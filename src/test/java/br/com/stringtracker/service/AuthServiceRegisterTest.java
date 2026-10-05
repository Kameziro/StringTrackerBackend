package br.com.stringtracker.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A categoria só é opcional no cadastro do convite; o do app continua exigindo (1 a 8). */
@QuarkusTest
class AuthServiceRegisterTest {

    @Inject
    AuthService authService;

    @Test
    void register_withoutCategory_isRejected() {
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> authService.register("sem.categoria@example.com", "segredo1", "Sem Categoria", null, 1L));

        assertEquals("Categoria inválida", error.getMessage());
    }

    @Test
    void register_withCategoryOutOfRange_isRejected() {
        assertThrows(BadRequestException.class,
                () -> authService.register("fora.faixa@example.com", "segredo1", "Fora Faixa", 9, 1L));
    }

    @Test
    void registerInvitee_withCategoryOutOfRange_isRejected() {
        assertThrows(BadRequestException.class,
                () -> authService.registerInvitee("convidado.faixa@example.com", "segredo1", "Convidado", 0, 1L));
    }
}
