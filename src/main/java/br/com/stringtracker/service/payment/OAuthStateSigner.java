package br.com.stringtracker.service.payment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Assina o {@code state} do OAuth do Mercado Pago. Ele amarra o clube, o admin que iniciou a
 * conexão e uma validade de 10 minutos, então o callback não confia em nada que venha da URL.
 * Formato: {@code base64url("clubId.userId.expiraEmEpochSegundos") + "." + base64url(HMAC-SHA256)}.
 */
@ApplicationScoped
public class OAuthStateSigner {

    public record OAuthState(long clubId, long userId) {
    }

    private static final Duration VALIDITY = Duration.ofMinutes(10);
    private static final int MIN_KEY_CHARS = 32;
    private static final String INVALID = "State do OAuth inválido ou expirado";

    private final SecretKeySpec key;

    public OAuthStateSigner(@ConfigProperty(name = "mp.state.key") String secret) {
        if (secret == null || secret.length() < MIN_KEY_CHARS) {
            throw new IllegalArgumentException("MP_STATE_KEY deve ter ao menos " + MIN_KEY_CHARS + " caracteres");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String issue(long clubId, long userId, Instant now) {
        String payload = clubId + "." + userId + "." + now.plus(VALIDITY).getEpochSecond();
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(encoded);
    }

    /** @throws BadRequestException se a assinatura não confere, o formato é inválido ou o state expirou */
    public OAuthState verify(String state, Instant now) {
        int dot = state == null ? -1 : state.indexOf('.');
        if (dot < 0) {
            throw new BadRequestException(INVALID);
        }
        String encoded = state.substring(0, dot);
        byte[] expected = sign(encoded).getBytes(StandardCharsets.UTF_8);
        byte[] actual = state.substring(dot + 1).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BadRequestException(INVALID);
        }
        String[] parts = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8).split("\\.");
        if (Instant.ofEpochSecond(Long.parseLong(parts[2])).isBefore(now)) {
            throw new BadRequestException(INVALID);
        }
        return new OAuthState(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível", e);
        }
    }
}
