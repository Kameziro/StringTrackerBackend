package br.com.stringtracker.service.payment;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Valida o cabeçalho {@code x-signature: ts=<timestamp>,v1=<hash>} das notificações do Mercado Pago (spike T1):
 * o hash é o HMAC-SHA256 em hexadecimal, com a chave secreta do webhook, do manifesto
 * {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;}. A comparação é em tempo constante.
 */
@ApplicationScoped
public class WebhookSignatureVerifier {

    private static final String HMAC = "HmacSHA256";

    private final SecretKeySpec key;

    public WebhookSignatureVerifier(@ConfigProperty(name = "mp.webhook.secret") String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC);
    }

    public boolean isValid(String signatureHeader, String requestId, String dataId) {
        if (signatureHeader == null || requestId == null || dataId == null) {
            return false;
        }
        String timestamp = null;
        String hash = null;
        for (String part : signatureHeader.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && pair[0].equals("ts")) {
                timestamp = pair[1];
            } else if (pair.length == 2 && pair[0].equals("v1")) {
                hash = pair[1];
            }
        }
        if (timestamp == null || hash == null) {
            return false;
        }
        // O Mercado Pago assina ids alfanuméricos em minúsculas; aceita-se também o id como veio na URL.
        return matches(hash, manifest(dataId.toLowerCase(Locale.ROOT), requestId, timestamp))
                | matches(hash, manifest(dataId, requestId, timestamp));
    }

    private static String manifest(String id, String requestId, String timestamp) {
        return "id:" + id + ";request-id:" + requestId + ";ts:" + timestamp + ";";
    }

    private boolean matches(String receivedHash, String manifest) {
        byte[] expected = HexFormat.of().formatHex(hmac(manifest)).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, receivedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private byte[] hmac(String manifest) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponível", e);
        }
    }
}
