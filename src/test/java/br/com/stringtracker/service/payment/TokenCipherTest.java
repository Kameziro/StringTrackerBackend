package br.com.stringtracker.service.payment;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final TokenCipher cipher = new TokenCipher(KEY);

    @Test
    void encryptThenDecrypt_returnsTheOriginalText() {
        String token = "APP_USR-123456789-token-çãõ";

        assertEquals(token, cipher.decrypt(cipher.encrypt(token)));
    }

    @Test
    void encryptingTheSameTextTwice_producesDifferentCiphertexts() {
        String token = "APP_USR-123456789-token";

        assertNotEquals(cipher.encrypt(token), cipher.encrypt(token));
    }

    @Test
    void decryptingTamperedCiphertext_throws() {
        byte[] payload = Base64.getDecoder().decode(cipher.encrypt("APP_USR-123456789-token"));
        payload[payload.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(payload);

        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    void missingKey_failsOnConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new TokenCipher(null));
        assertThrows(IllegalArgumentException.class, () -> new TokenCipher("  "));
    }

    @Test
    void keyThatIsNot32Bytes_failsOnConstruction() {
        String shortKey = Base64.getEncoder().encodeToString(Arrays.copyOf(new byte[32], 16));

        assertThrows(IllegalArgumentException.class, () -> new TokenCipher(shortKey));
    }
}
