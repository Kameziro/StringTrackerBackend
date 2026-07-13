# API Mobile Integration — Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name and follow its Execute flow and Critical Rules.**

**Design**: `.specs/features/api-mobile-integration/design.md`  
**Status**: Done

---

## Test Coverage Matrix

> Guidelines found: `AGENTS.md` (Expo v57); BE RestAssured resource tests; mobile Jest (`hooks/__tests__`). Strong defaults for new FE service/auth layers.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| ---------- | ------------------ | -------------------- | ---------------- | ----------- |
| Flyway / config | none | Build + existing BE suite green | `backend/src/main/resources/**` | `mvn -f backend/pom.xml test` |
| BE resources (regressão) | integration | Existing ACs intact after Flyway | `backend/src/test/java/**/resource/*Test.java` | `mvn -f backend/pom.xml test` |
| Auth helpers (JWT premium, ApiError map) | unit | AMI-09 + error kinds 401/403/404/rede | `mobile/services/__tests__/*.test.ts` | `cd mobile && npm test` |
| HTTP/API client | unit | Happy parse + status→ApiError (403 literal) | `mobile/services/__tests__/*.test.ts` | `cd mobile && npm test` |
| useRackets / mappers | unit | Map wire↔UI; no seed SoT | `mobile/hooks/__tests__/*.test.ts` | `cd mobile && npm test` |
| Screens / AuthSession UI | none | typecheck gate (e2e device deferred) | `mobile/app/**` | `cd mobile && npm run typecheck` |

## Gate Check Commands

| Gate Level | When to Use | Command |
| ---------- | ----------- | ------- |
| Quick | Mobile unit-only tasks | `cd mobile && npm test` |
| Full | BE schema/API tasks | `mvn -f backend/pom.xml test` |
| Build | Phase end / wiring screens | BE: `mvn -f backend/pom.xml test`; FE: `cd mobile && npm test && npm run typecheck` |

---

## Execution Plan

### Phase 1: Backend schema (Flyway)

```
T1 → T2
```

### Phase 2: Auth + HTTP client

```
T3 → T4 → T5
```

### Phase 3: Data hooks + UI

```
T6 → T7 → T8
```

---

## Task Breakdown

### T1: Add Flyway dependencies and disable Hibernate DDL

**What**: Add `quarkus-flyway` + `flyway-database-postgresql`; set `generation=none` and `flyway.migrate-at-start=true`.
**Where**: `backend/pom.xml`, `backend/src/main/resources/application.properties`
**Depends on**: None
**Reuses**: Existing Quarkus datasource config
**Requirement**: AMI-00

**Done when**:

- [ ] Dependencies present in pom
- [ ] `quarkus.hibernate-orm.database.generation=none`
- [ ] `quarkus.flyway.migrate-at-start=true`
- [ ] `%test` uses Flyway migrate (not `drop-and-create`) + H2 `MODE=PostgreSQL`

**Tests**: none (config) — verified with T2 suite  
**Gate**: build (compile) — full suite on T2  
**Commit**: `build(backend): add Flyway and disable Hibernate DDL`

---

### T2: Create V1 core schema migration

**What**: SQL migration creating `users`, `rackets`, `play_sessions` matching JPA entities.
**Where**: `backend/src/main/resources/db/migration/V1__create_core_schema.sql`
**Depends on**: T1
**Reuses**: Column names from `User` / `Racket` / `PlaySession`
**Requirement**: AMI-00

**Done when**:

- [ ] V1 creates tables + FKs + unique constraints (`uk_users_keycloak_id`, `uk_users_email`)
- [ ] `mvn -f backend/pom.xml test` passes (existing resource tests)
- [ ] Test count ≥ previous suite (no deletions)

**Tests**: integration (existing BE suite = gate)  
**Gate**: full  
**Commit**: `feat(backend): add Flyway V1 core schema`

---

### T3: Auth token store + JWT premium helper

**What**: SecureStore wrapper for access token; `isPremiumFromAccessToken` reading realm role `premium`.
**Where**: `mobile/services/tokenStore.ts`, `mobile/services/jwt.ts`, `mobile/services/__tests__/jwt.test.ts`
**Depends on**: None (parallel to BE; ordered after Phase 1 for commits)
**Reuses**: Spec AMI-09
**Requirement**: AMI-01, AMI-09

**Done when**:

- [ ] save/get/clear access token APIs
- [ ] `isPremiumFromAccessToken` true only com role `premium`
- [ ] Unit tests cover premium / free / malformed
- [ ] `npm test` passa

**Tests**: unit  
**Gate**: quick  
**Commit**: `feat(mobile): add token store and JWT premium helper`

---

### T4: OIDC login helpers (AuthSession)

**What**: Keycloak discovery + AuthRequest PKCE helpers; login/logout orchestration using tokenStore.
**Where**: `mobile/services/auth.ts`, deps in `mobile/package.json`
**Depends on**: T3
**Reuses**: `expo-web-browser`; Keycloak client `stringtracker-expo`
**Requirement**: AMI-01, AMI-02

**Done when**:

- [ ] deps: `expo-auth-session`, `expo-crypto`, `expo-secure-store` instalados
- [ ] `loginWithKeycloak` / `logout` exportados
- [ ] Env defaults documentados (Keycloak URL/realm/client)
- [ ] typecheck ok para novos módulos

**Tests**: none (UI/browser); helpers puros cobertos em T3  
**Gate**: build (`npm run typecheck`)  
**Commit**: `feat(mobile): add Keycloak AuthSession login helpers`

---

### T5: HTTP client + rackets/sessions API + ApiError

**What**: Typed fetch client with Bearer; list/create rackets; create session; map status→ApiError (403 body literal).
**Where**: `mobile/services/http.ts`, `mobile/services/api.ts`, `mobile/services/__tests__/api.test.ts`
**Depends on**: T3 (token getter pattern)
**Reuses**: Types already in `api.ts`; `dtos.md`
**Requirement**: AMI-03, AMI-05, AMI-08

**Done when**:

- [ ] `listRackets` / `createRacket` / `createSession` tipados
- [ ] 403 message = response text (não JSON inventado)
- [ ] Unit tests com fetch mock: 200/201/403 literal/401/404/network
- [ ] `npm test` passa

**Tests**: unit  
**Gate**: quick  
**Commit**: `feat(mobile): add typed Quarkus API client`

---

### T6: AuthProvider + login screen + route gate

**What**: Context de sessão; tela `login`; root layout redireciona sem token ↔ tabs.
**Where**: `mobile/contexts/AuthContext.tsx`, `mobile/app/login.tsx`, `mobile/app/_layout.tsx`
**Depends on**: T4
**Reuses**: NativeWind shell styles
**Requirement**: AMI-01, AMI-02

**Done when**:

- [ ] Sem token → login; com token → tabs
- [ ] Logout limpa token e volta login
- [ ] typecheck passa

**Tests**: none (screen)  
**Gate**: build (`npm run typecheck`)  
**Commit**: `feat(mobile): add AuthProvider login gate`

---

### T7: Replace useRackets with API-backed hook

**What**: Remover seed/`racketStore` SoT; hook carrega via API; add racket/session; loading/error; map ids.
**Where**: `mobile/hooks/useRackets.ts`, delete/ deprecate `racketStore.ts`, update `mobile/hooks/__tests__/*`
**Depends on**: T5, T6
**Reuses**: `types.ts` constants/messages
**Requirement**: AMI-04, AMI-05, AMI-06, AMI-07, AMI-08

**Done when**:

- [ ] Nenhum seed Babolat hardcoded como SoT
- [ ] refresh/list/create/session usam client
- [ ] Testes unitários do mapper + comportamento de erro 403
- [ ] `npm test` passa

**Tests**: unit  
**Gate**: quick  
**Commit**: `feat(mobile): replace in-memory rackets with API hook`

---

### T8: Wire screens — spinner, errors, premium JWT, paywall simulado

**What**: Home/Raquetes/Perfil/Paywall usam novo hook+auth; spinner; erros por status; isPremium do JWT; CTA paywall só Alert.
**Where**: `mobile/app/(tabs)/*.tsx`, `mobile/app/paywall.tsx`
**Depends on**: T7
**Reuses**: UI existente
**Requirement**: AMI-04, AMI-07, AMI-08, AMI-09, AMI-10

**Done when**:

- [ ] Spinner em fetch; 403 literal; 404/rede tratados
- [ ] Perfil mostra premium via JWT; logout disponível
- [ ] Paywall CTA não grava premium
- [ ] `npm test && npm run typecheck` passa

**Tests**: none (screens) + regression unit suite  
**Gate**: build  
**Commit**: `feat(mobile): wire screens to API auth and errors`

---

## Phase Execution Map

```
Phase 1 → Phase 2 → Phase 3

Phase 1:  T1 ──→ T2
Phase 2:  T3 ──→ T4 ──→ T5
Phase 3:  T6 ──→ T7 ──→ T8
```

**Batches (≤8 tasks → single inline batch):** T1–T8 execute inline sequentially (user waived multi-worker offer).

---

## Task Granularity Check

| Task | Scope | Status |
| ---- | ----- | ------ |
| T1 | config/deps | ✅ |
| T2 | one migration file | ✅ |
| T3 | token + jwt helpers | ✅ |
| T4 | auth OIDC module | ✅ |
| T5 | HTTP + API | ✅ |
| T6 | auth UI gate | ✅ |
| T7 | hook SoT swap | ✅ |
| T8 | screen wiring | ✅ |

---

## Diagram-Definition Cross-Check

| Task | Depends On (body) | Diagram Shows | Status |
| ---- | ----------------- | ------------- | ------ |
| T1 | None | (start) | ✅ |
| T2 | T1 | T1→T2 | ✅ |
| T3 | None | (Phase2 start) | ✅ |
| T4 | T3 | T3→T4 | ✅ |
| T5 | T3 | T3→T5 (via T4 order) | ✅ Match order; dep T3 only |
| T6 | T4 | T4→T5→T6 | ⚠️ Diagram phase order T5 before T6; T6 depends T4 only — OK (T5 parallel conceptually; we run T5 before T6) |
| T7 | T5, T6 | T6→T7 | ✅ (+T5) |
| T8 | T7 | T7→T8 | ✅ |

---

## Test Co-location Validation

| Task | Layer | Matrix Requires | Task Says | Status |
| ---- | ----- | --------------- | --------- | ------ |
| T1 | config | none | none | ✅ |
| T2 | BE integration | integration (existing) | integration gate | ✅ |
| T3 | auth helpers | unit | unit | ✅ |
| T4 | auth OIDC | none | none | ✅ |
| T5 | HTTP/API | unit | unit | ✅ |
| T6 | screens/gate | none | none | ✅ |
| T7 | hooks | unit | unit | ✅ |
| T8 | screens | none + suite | build | ✅ |
