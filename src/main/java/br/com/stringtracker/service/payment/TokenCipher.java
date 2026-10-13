package br.com.stringtracker.service.payment;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra os tokens OAuth dos clubes com AES-256-GCM antes de gravá-los no banco.
 * A chave vem de {@code TOKEN_CIPHER_KEY} (32 bytes em Base64) e fica fora do banco.
 * O texto cifrado é {@code Base64(iv || cifrado+tag)}, com IV aleatório a cada chamada.
 */
@ApplicationScoped
@Startup
public class TokenCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(@ConfigProperty(name = "token.cipher.key") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("TOKEN_CIPHER_KEY é obrigatória");
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Key.trim());
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException("TOKEN_CIPHER_KEY deve ter 32 bytes em Base64");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plainText) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(IV_BYTES + encrypted.length).put(iv).put(encrypted).array();
            return Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar o token", e);
        }
    }

    /** @throws IllegalArgumentException se o texto foi adulterado, truncado ou cifrado com outra chave */
    public String decrypt(String cipherText) {
        try {
            byte[] payload = Base64.getDecoder().decode(cipherText);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Token cifrado inválido", e);
        }
    }
}
