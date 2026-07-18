package br.com.stringtracker.service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class MinioObjectStorage {

    private static final Logger LOG = Logger.getLogger(MinioObjectStorage.class);

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private static final Map<String, String> EXT_BY_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    /** Prefixo relativo servido por {@code MediaResource}. */
    public static final String MEDIA_PREFIX = "/api/media/";
    /** Prefixo legado (user avatars antes do proxy unificado). */
    private static final String LEGACY_AVATAR_PREFIX = "/api/media/avatars/";

    public enum GroupImageKind {
        AVATAR("avatar"),
        BANNER("banner");

        private final String fileStem;

        GroupImageKind(String fileStem) {
            this.fileStem = fileStem;
        }

        public String fileStem() {
            return fileStem;
        }
    }

    @ConfigProperty(name = "minio.endpoint")
    String endpoint;

    @ConfigProperty(name = "minio.access-key")
    String accessKey;

    @ConfigProperty(name = "minio.secret-key")
    String secretKey;

    @ConfigProperty(name = "minio.bucket.avatars")
    String avatarsBucket;

    @ConfigProperty(name = "minio.region")
    String region;

    private MinioClient client;

    @PostConstruct
    void init() {
        client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .region(region)
                .build();
    }

    public String uploadUserAvatar(long userId, InputStream data, long size, String contentType) {
        String ext = validateAndExt(contentType, size);
        String objectKey = "users/" + userId + "/avatar." + ext;
        putObject(objectKey, data, size, normalizeContentType(contentType));
        return MEDIA_PREFIX + objectKey + "?v=" + System.currentTimeMillis();
    }

    public String uploadGroupImage(
            long groupId,
            GroupImageKind kind,
            InputStream data,
            long size,
            String contentType
    ) {
        String ext = validateAndExt(contentType, size);
        String objectKey = "groups/" + groupId + "/" + kind.fileStem() + "." + ext;
        putObject(objectKey, data, size, normalizeContentType(contentType));
        return MEDIA_PREFIX + objectKey + "?v=" + System.currentTimeMillis();
    }

    public InputStream openObject(String objectKey) {
        try {
            return client.getObject(
                    GetObjectArgs.builder()
                            .bucket(avatarsBucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            LOG.warn("Objeto não encontrado no MinIO: " + objectKey, e);
            throw new NotFoundException("Arquivo não encontrado");
        }
    }

    public String probeContentType(String objectKey) {
        try {
            var stat = client.statObject(
                    StatObjectArgs.builder()
                            .bucket(avatarsBucket)
                            .object(objectKey)
                            .build()
            );
            String type = stat.contentType();
            if (type != null && !type.isBlank()) {
                return type;
            }
        } catch (Exception e) {
            LOG.debug("statObject falhou para " + objectKey + ": " + e.getMessage());
        }
        String lower = objectKey.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    public void deleteObjectIfPresent(String storedUrl) {
        String objectKey = extractObjectKey(storedUrl);
        if (objectKey == null) {
            return;
        }
        try {
            client.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(avatarsBucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            LOG.warn("Não foi possível remover objeto antigo: " + storedUrl, e);
        }
    }

    public String extractObjectKey(String storedUrl) {
        if (storedUrl == null || storedUrl.isBlank()) {
            return null;
        }
        String value = stripQuery(storedUrl);

        if (value.startsWith(LEGACY_AVATAR_PREFIX)) {
            return value.substring(LEGACY_AVATAR_PREFIX.length());
        }
        if (value.startsWith(MEDIA_PREFIX)) {
            return value.substring(MEDIA_PREFIX.length());
        }

        String marker = "/" + avatarsBucket + "/";
        int idx = value.indexOf(marker);
        if (idx >= 0) {
            return value.substring(idx + marker.length());
        }
        return null;
    }

    public String toClientMediaUrl(String storedUrl) {
        if (storedUrl == null || storedUrl.isBlank()) {
            return null;
        }
        if (storedUrl.startsWith(LEGACY_AVATAR_PREFIX)) {
            return MEDIA_PREFIX + storedUrl.substring(LEGACY_AVATAR_PREFIX.length());
        }
        if (storedUrl.startsWith(MEDIA_PREFIX)) {
            return storedUrl;
        }
        String key = extractObjectKey(storedUrl);
        if (key == null) {
            return storedUrl;
        }
        int query = storedUrl.indexOf('?');
        String suffix = query >= 0 ? storedUrl.substring(query) : "";
        return MEDIA_PREFIX + key + suffix;
    }

    private void putObject(String objectKey, InputStream data, long size, String contentType) {
        try {
            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(avatarsBucket)
                            .object(objectKey)
                            .stream(data, size, -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception e) {
            LOG.error("Falha ao enviar objeto ao MinIO: " + objectKey, e);
            throw new InternalServerErrorException("Falha ao salvar a imagem");
        }
    }

    private static String stripQuery(String value) {
        int query = value.indexOf('?');
        return query >= 0 ? value.substring(0, query) : value;
    }

    private static String normalizeContentType(String contentType) {
        return contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
    }

    private static String validateAndExt(String contentType, long size) {
        if (contentType == null || contentType.isBlank()) {
            throw new BadRequestException("Content-Type da imagem é obrigatório");
        }
        String normalized = normalizeContentType(contentType);
        if (!ALLOWED_CONTENT_TYPES.contains(normalized)) {
            throw new BadRequestException("Use JPEG, PNG ou WebP");
        }
        if (size <= 0 || size > MAX_BYTES) {
            throw new BadRequestException("A imagem deve ter no máximo 5 MB");
        }
        return EXT_BY_TYPE.get(normalized);
    }
}
