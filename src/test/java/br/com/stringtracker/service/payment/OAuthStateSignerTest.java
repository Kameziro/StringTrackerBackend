package br.com.stringtracker.service.payment;

import br.com.stringtracker.service.payment.OAuthStateSigner.OAuthState;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OAuthStateSignerTest {

    private static final String KEY = "unit-test-oauth-state-key-0123456789ab";

    private final OAuthStateSigner signer = new OAuthStateSigner(KEY);
    private final Instant now = Instant.parse("2026-10-05T12:00:00Z");

    @Test
    void verify_returnsTheClubAndUserTheStateWasIssuedFor() {
        String state = signer.issue(7, 42, now);

        assertEquals(new OAuthState(7, 42), signer.verify(state, now.plus(Duration.ofMinutes(9))));
    }

    @Test
    void verify_rejectsAStateOlderThanTenMinutes() {
        String state = signer.issue(7, 42, now);

        assertThrows(BadRequestException.class, () -> signer.verify(state, now.plus(Duration.ofMinutes(11))));
    }

    @Test
    void verify_rejectsATamperedPayload() {
        String state = signer.issue(7, 42, now);
        String signature = state.substring(state.indexOf('.') + 1);
        long expiry = now.plus(Duration.ofMinutes(10)).getEpochSecond();
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("8.42." + expiry).getBytes(StandardCharsets.UTF_8));

        assertThrows(BadRequestException.class, () -> signer.verify(forgedPayload + "." + signature, now));
    }

    @Test
    void verify_rejectsAStateSignedWithAnotherKey() {
        String foreign = new OAuthStateSigner("another-oauth-state-key-0123456789abcdef").issue(7, 42, now);

        assertThrows(BadRequestException.class, () -> signer.verify(foreign, now));
    }

    @Test
    void verify_rejectsGarbageAndMissingState() {
        assertThrows(BadRequestException.class, () -> signer.verify("not-a-state", now));
        assertThrows(BadRequestException.class, () -> signer.verify(null, now));
    }

    @Test
    void constructor_rejectsAShortKey() {
        assertThrows(IllegalArgumentException.class, () -> new OAuthStateSigner("too-short"));
    }
}
