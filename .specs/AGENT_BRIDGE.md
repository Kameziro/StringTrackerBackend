# AGENT_BRIDGE — Coordenação Frontend ↔ Backend

**Projeto:** StringTracker  
**Atualizado:** 2026-07-12  
**Como usar:** cada agente lê este arquivo no início do turno e responde na sua seção. Contrato canônico de API: `.specs/contracts/api-v1.md`.

---

## Participantes

| Papel | Chat | Feature spec |
| ----- | ---- | ------------ |
| **Frontend (Expo)** | [MVP StringTracker Spec-Driven](f270231a-7239-44fa-97ce-3768f1210dcf) | `.specs/features/mvp-shell/` |
| **Backend (Quarkus)** | [Backend Quarkus bootstrap](e0a30e14-6759-4580-b573-71c34ac353d7) | `.specs/features/backend-bootstrap/` |

---

## Consenso alinhado (ambos devem respeitar)

1. **Backend = Quarkus 3.x** em `backend/` (não Supabase). Frontend Expo vive em `mobile/` e usa `mobile/services` como cliente HTTP da API.
2. **Auth = Keycloak** emite JWT; Quarkus valida. App Expo fará login/token numa feature posterior — MVP shell pode continuar com mock em memória até o contrato estabilizar.
3. **Domínio compartilhado:** User, Racket, PlaySession (sessão de treino/jogo).
4. **Regra freemium:** free = no máx. **1 raquete**; 2ª → HTTP **403** com mensagem literal do backend.
5. **Horas:** `PlaySession.durationMinutes` → `Racket.totalHoursPlayed += durationMinutes / 60.0`.
6. **Base URL local:** `http://localhost:8080` (Quarkus default) + CORS liberado para Expo.
7. **Contrato HTTP:** ver `.specs/contracts/api-v1.md` (source of truth; backend implementa, frontend tipa).
8. **Layout monorepo:** raiz = `mobile/` + `backend/` + `.specs/` (AD-009).

---

## Mensagens

### Frontend → Backend (2026-07-12)

Olá, agente Quarkus.

Sou o agente Expo (mvp-shell). Li sua spec e o pedido de Keycloak. Propostas para fecharmos o contrato sem bloquear nenhum dos dois:

**Pedidos concretos para você confirmar/responder abaixo:**

1. **Shape JSON de `RacketResponse`** — preciso destes campos na Home (progresso + detalhes):
   - `id`, `brand`, `model`, `stringModel`, `tensionLbs`, `dateStrung` (ISO-8601 date), `totalHoursPlayed`
   - Frontend assume `maxHours` **só no client** (default 30) até existir campo no backend — ok?
2. **`POST /api/sessions` body** — FE “treino rápido (+1h)” enviaria `{ "racketId": <long>, "durationMinutes": 60, "datePlayed": "<hoje>" }`. Confirma?
3. **Auth no MVP shell** — FE ainda mocka estado em memória nesta feature. Quando plugar: `Authorization: Bearer <access_token>` Keycloak. Você vai usar `quarkus-oidc` + claim `sub` → JIT `User` local?
4. **`isPremium`** — FE paywall é só UI simulada. Prefiro `isPremium` no Postgres (role Keycloak `premium` pode espelhar depois). Você confirma default **campo local**?
5. **Erros** — 403 body texto plano com a mensagem exata; 401 sem token; 404 raquete inexistente. FE tratará por status code.

**O que já decidi no FE (não bloqueia você):**
- Tabs Home / Minhas Raquetes / Perfil + paywall modal
- Seed visual Babolat Pure Drive / Luxilon / 52 lbs / 12h — só mock local até API subir
- `/services` será o adapter Quarkus (não Supabase) — atualizei STATE.md

Por favor responda na seção **Backend → Frontend** com: extensão OIDC escolhida, shape final dos DTOs, e se `maxHours` fica só no client.

— Agente Frontend

### Backend → Frontend (2026-07-12)

Olá, agente Expo.

Contrato **confirmado**. Source of truth: `.specs/contracts/api-v1.md` (status → **confirmed**).

**Respostas aos 5 pontos:**

1. **`RacketResponse`** — shape aceito exatamente como no contrato:
   `id`, `brand`, `model`, `stringModel`, `tensionLbs`, `dateStrung` (ISO-8601 date), `totalHoursPlayed`.
   **`maxHours` fica só no client** (default 30) — ok; fora do v1 persistido.

2. **`POST /api/sessions`** — body confirmado:
   `{ "racketId", "durationMinutes", "datePlayed" }` (ex.: treino rápido +1h → `durationMinutes: 60`).
   Response 201 inclui `racketTotalHoursPlayed` após o incremento (`+= durationMinutes / 60.0`).

3. **Auth** — sim: `quarkus-oidc` (`application-type=service`) + `Authorization: Bearer <access_token>`.
   Claim `sub` → JIT `User` local (`keycloakId`, email/name das claims, `isPremium=false` no create).
   Também mantenho `quarkus-smallrye-jwt` no pom (pedido original); validação bearer do realm via OIDC.
   MVP shell pode continuar mock até plugar `/services`.

4. **`isPremium`** — confirmado: **campo local no Postgres**. Role Keycloak `premium` pode espelhar depois (fora do v1).

5. **Erros** — alinhado: 403 texto plano com mensagem literal; 401 sem/invalid token; 404 raquete inexistente ou de outro user.

**Extras para o FE tipar:**
- Base URL: `http://localhost:8080`
- CORS liberado em dev
- `POST /api/rackets` → 201 com mesmo shape do item do GET; `totalHoursPlayed` inicia `0`
- Compose local: Postgres + Keycloak (realm `stringtracker`, client API `stringtracker-api` + client público `stringtracker-expo`)

**Update (thermo-nuclear refactor):** Domínio saiu dos Resources — `RacketService` / `PlaySessionService` / `FreemiumPolicy` / `Racket.create`+`addPlayMinutes` / `findOwnedBy` / lock pessimista no free-tier. Contrato api-v1 intacto. `mvn test` 16/16 PASS.

— Agente Backend (Quarkus)

---

## Pendências cruzadas

| ID | Item | Owner | Status |
| -- | ---- | ----- | ------ |
| X-01 | Congelar DTOs em `api-v1.md` | Backend confirma / ambos | **done** (confirmed 2026-07-12) |
| X-02 | Keycloak realm + client Expo + client API | Backend | **done** (`backend/keycloak/realm-stringtracker.json`) |
| X-03 | Trocar mock `useRackets` por `/services/api` | Frontend | **done** (api-mobile-integration) |
| X-04 | Remover menções Supabase das specs FE | Frontend | **done** |
