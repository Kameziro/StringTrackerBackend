# StringTracker API (Quarkus)

API Java/Quarkus do rastreador de vida útil de cordas. Contrato FE↔BE: [`.specs/contracts/api-v1.md`](../.specs/contracts/api-v1.md).

## Stack

- Quarkus 3.20 / Java 21
- REST (`quarkus-rest-jackson`), Hibernate ORM Panache, PostgreSQL
- Auth: Keycloak via `quarkus-oidc` (valida Bearer JWT). Login do app: BFF `POST /api/auth/login` (mobile não fala com Keycloak — AD-012).

## Configuração (.env)

```bash
cd backend
cp .env.example .env   # se ainda não existir
# edite secrets: POSTGRES_PASSWORD, OIDC_CLIENT_SECRET, KEYCLOAK_ADMIN_PASSWORD
```

O Quarkus carrega `backend/.env` no `quarkus:dev`. O `docker compose` usa as mesmas variáveis para Postgres/Keycloak.

## Subir infra local

**Só PostgreSQL** (API em `localhost:5432`):

```bash
cd backend
docker compose up -d postgres
```

**Stack completa** (Postgres + Keycloak):

```bash
docker compose --profile full up -d
```

- Keycloak: `http://localhost:8180` (admin via `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` no `.env`)
- Realm: `stringtracker`
- Clients: `stringtracker-api` (bearer + secret no `.env`), `stringtracker-expo` (público, PKCE)

### Usuários demo

| User | Senha | Nota |
| ---- | ----- | ---- |
| `free.player` | `free123` | free tier |
| `premium.player` | `premium123` | role `premium` (flag local `isPremium` ainda é setada na API) |

## Rodar a API

Requer JDK 21 + Maven 3.9+:

```bash
cd backend
./mvnw quarkus:dev
# ou
mvn quarkus:dev
```

Base URL: `http://localhost:8080`

### Login via API (app / curl)

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"free.player\",\"password\":\"free123\"}"
```

### Obter token direto no Keycloak (só debug)

```bash
curl -s -X POST "http://localhost:8180/realms/stringtracker/protocol/openid-connect/token" \
  -d "client_id=stringtracker-expo" \
  -d "username=free.player" \
  -d "password=free123" \
  -d "grant_type=password" | jq -r .access_token
```

### Exemplo

```bash
TOKEN=...
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/rackets
```

## Testes

```bash
mvn test
# sem JDK local:
docker run --rm -v "${PWD}:/project" -w /project maven:3.9.9-eclipse-temurin-21 mvn test
```

## Endpoints (v1)

| Method | Path | Auth |
| ------ | ---- | ---- |
| GET | `/api/rackets` | Bearer |
| POST | `/api/rackets` | Bearer (free: máx. 1) |
| POST | `/api/sessions` | Bearer |
