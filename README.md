# Padel Match API (Quarkus)

API do app de matching de jogos de padel. Contrato: [`.specs/contracts/api-v1.md`](.specs/contracts/api-v1.md).

## Stack

- Quarkus 3.20 / Java 21
- REST + Hibernate ORM Panache + PostgreSQL + Flyway
- Auth: Keycloak (`quarkus-oidc`) + BFF `POST /api/auth/login`
- Push: Expo Push API

## Subir infra

```bash
# Postgres + MinIO (bucket padelmatch-avatars para fotos de perfil)
docker compose up -d postgres minio minio-init

# ou stack completa (inclui Keycloak):
docker compose --profile full up -d
```

| Serviço | URL |
|---------|-----|
| API | `http://localhost:8080` |
| MinIO S3 API | `http://localhost:9000` |
| MinIO Console | `http://localhost:9001` |

Credenciais MinIO: `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` no `.env`.

**Foto de perfil:** `POST /api/me/profile/avatar` (`multipart` campo `file`) grava no bucket e devolve `avatarUrl`.  
No iPhone físico, defina `MINIO_PUBLIC_BASE_URL=http://<IP-da-sua-rede>:9000` para as imagens abrirem no aparelho.

```bash
mvn quarkus:dev
```

Base URL: `http://localhost:8080`

### Login

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"free.player\",\"password\":\"free123\"}"
```
