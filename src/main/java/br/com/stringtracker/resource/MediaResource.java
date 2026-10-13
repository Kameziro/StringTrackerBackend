package br.com.stringtracker.resource;

import br.com.stringtracker.service.MinioObjectStorage;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.InputStream;
import java.util.Locale;

/**
 * Proxy público de mídia (RN Image não envia Authorization).
 * Paths: users/{id}/avatar.*, groups/{id}/avatar.*, groups/{id}/banner.*,
 * clubs/{id}/logo.*, clubs/{id}/photos/{uuid}.*
 */
@Path("/api/media")
@PermitAll
public class MediaResource {

    @Inject
    MinioObjectStorage minioObjectStorage;

    @GET
    @Path("/{objectKey:.+}")
    public Response getObject(@PathParam("objectKey") String objectKey) {
        String key = sanitizeObjectKey(objectKey);
        try {
            String contentType = minioObjectStorage.probeContentType(key);
            InputStream stream = minioObjectStorage.openObject(key);
            StreamingOutput body = output -> {
                try (InputStream in = stream) {
                    in.transferTo(output);
                }
            };
            return Response.ok(body)
                    .type(contentType)
                    .header("Cache-Control", "public, max-age=3600")
                    .build();
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new NotFoundException("Arquivo não encontrado");
        }
    }

    private static String sanitizeObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new NotFoundException("Arquivo não encontrado");
        }
        String key = objectKey;
        // legado: /api/media/avatars/users/...
        if (key.startsWith("avatars/")) {
            key = key.substring("avatars/".length());
        }
        int query = key.indexOf('?');
        if (query >= 0) {
            key = key.substring(0, query);
        }
        if (key.contains("..") || key.startsWith("/")) {
            throw new NotFoundException("Arquivo não encontrado");
        }
        boolean allowedPrefix = key.startsWith("users/") || key.startsWith("groups/") || key.startsWith("clubs/");
        if (!allowedPrefix) {
            throw new NotFoundException("Arquivo não encontrado");
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp"))) {
            throw new NotFoundException("Arquivo não encontrado");
        }
        return key;
    }
}
