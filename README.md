# Padel Match API (Quarkus)

API do app de matching de jogos de padel. Contrato: [`.specs/contracts/api-v1.md`](.specs/contracts/api-v1.md).

## Stack

- Quarkus 3.20 / Java 21
- REST + Hibernate ORM Panache + PostgreSQL + Flyway
- Auth: Keycloak (`quarkus-oidc`) + BFF `POST /api/auth/login`
- Push: Expo Push API

## Subir infra

```bash
docker compose up -d postgres
# ou stack completa:
docker compose --profile full up -d
```

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
