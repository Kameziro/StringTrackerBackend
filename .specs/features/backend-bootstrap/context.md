# Backend Quarkus Bootstrap — Context

**Gathered:** 2026-07-12
**Spec:** `.specs/features/backend-bootstrap/spec.md`
**Status:** Ready for design

---

## Feature Boundary

Bootstrap da API Quarkus em `backend/` com domínio User/Racket/PlaySession, endpoints de raquetes (limite free) e sessões (acúmulo de horas), autenticação via **Keycloak (JWT/OIDC)**, PostgreSQL local e CORS para o Expo.

**Contrato canônico FE↔BE:** `.specs/contracts/api-v1.md` (confirmed). Coordenação: `.specs/AGENT_BRIDGE.md`.

---

## Implementation Decisions

### Auth / Keycloak

- Keycloak emite e gerencia tokens; API valida Bearer JWT
- Extensão principal: `quarkus-oidc` (`application-type=service`) — caminho oficial Quarkus + Keycloak
- Também incluir `quarkus-smallrye-jwt` conforme pedido original (disponível; validação de bearer do realm via OIDC)
- Endpoints `/api/*` exigem `@Authenticated`
- Identidade = claim `sub` do access token

### User local ↔ Keycloak

- JIT provisioning: no 1º request autenticado, criar `User` com `keycloakId=sub`, `email`, `name` (claims), `isPremium=false`
- Campo extra `keycloakId` (único) além do modelo pedido — necessário para ligar ao Keycloak

### isPremium

- Campo local no Postgres (`User.isPremium`)
- Default `false` no JIT; upgrade Premium é responsabilidade futura (admin/API)

### Ambiente local

- `docker-compose.yml` com Postgres (`stringtracker`) + Keycloak
- Realm export `keycloak/realm-stringtracker.json` (client `stringtracker-api`, usuários free/premium de demo)
- Dev mode: preferir Dev Services Keycloak quando possível; compose documentado para stack completa

### Domínio / API (api-v1.md)

- DTOs/JSON exatamente como em `.specs/contracts/api-v1.md`
- `RacketResponse`: id, brand, model, stringModel, tensionLbs, dateStrung, totalHoursPlayed
- `POST /api/sessions` request: racketId, durationMinutes, datePlayed
- `POST /api/sessions` response 201: id, racketId, durationMinutes, datePlayed, **racketTotalHoursPlayed**
- `totalHoursPlayed += durationMinutes / 60.0`
- `maxHours` **não** no backend (client-only, default 30)
- Erro 403 free: body **texto plano** com mensagem exata
- Bean Validation nos DTOs
- 404 se raquete inexistente **ou de outro user**

### Agent's Discretion

- Quarkus 3.20.x + Java 21
- Artifact `stringtracker-api`, group `br.com.stringtracker`
- Camada service mínima (`CurrentUserService`) para JIT — evita lógica de auth nos resources
- Testes: `@QuarkusTest` + RestAssured + `@TestSecurity` / OIDC test support (strong defaults; greenfield)

### Declined / Undiscussed → Assumptions

- Usuário respondeu "vai" → defaults do agente acima
- Formato 403 texto plano; horas = minutos/60.0

---

## Specific References

- Pedido original: packages model/repository/resource/dto; entidades e endpoints descritos
- Correção: Keycloak cuida do JWT (não stub `X-User-Id`)

---

## Deferred Ideas

- Signup/login UI no Expo
- Endpoint admin para toggle Premium
- CRUD completo de raquetes/sessões
- Soft-delete, optimistic locking
