# Backend Quarkus Bootstrap — Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name and follow its Execute flow and Critical Rules.**

---

**Design**: `.specs/features/backend-bootstrap/design.md`
**Status**: Done

---

## Test Coverage Matrix

> Guidelines found: none for Java — strong defaults applied. Greenfield Quarkus.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| ---------- | ------------------ | -------------------- | ---------------- | ----------- |
| Domain / CurrentUserService + business rules in resources | unit/integration `@QuarkusTest` | ACs 1:1 (limite free 403 msg, hours += min/60, list by user) | `src/test/java/br/com/stringtracker/**/*Test.java` | `mvn -f backend/pom.xml test` |
| REST resources | `@QuarkusTest` + RestAssured | Happy + edge (403, 404, 401) | same | same |
| Entity / config / compose | none | build gate | — | `mvn -f backend/pom.xml -DskipTests package` |

## Gate Check Commands

| Gate Level | When to Use | Command |
| ---------- | ----------- | ------- |
| Quick | Após tasks com testes | `mvn -f backend/pom.xml test` |
| Full | Após resources | `mvn -f backend/pom.xml test` |
| Build | Scaffold/entities/config | `mvn -f backend/pom.xml -DskipTests package` |

> Se `mvn`/`java` ausentes no host: `docker run --rm -v "${PWD}/backend:/project" -w /project maven:3.9.9-eclipse-temurin-21 mvn ...`

---

## Execution Plan

### Phase 1: Foundation

```
T1 → T2 → T3
```

### Phase 2: API + Auth wiring

```
T4 → T5 → T6 → T7
```

---

## Task Breakdown

### T1: Scaffold Maven Quarkus + application.properties + compose

**What**: Criar `backend/` com `pom.xml` (REST Jackson, Panache, PostgreSQL, OIDC, SmallRye JWT), `application.properties` (DB, generation=update, CORS, OIDC), `docker-compose.yml` (Postgres + Keycloak)
**Where**: `backend/pom.xml`, `backend/src/main/resources/application.properties`, `backend/docker-compose.yml`
**Depends on**: None
**Requirement**: BE-01, BE-02, BE-11

**Done when**:
- [ ] pom com extensões pedidas (+ oidc)
- [ ] JDBC `localhost:5432/stringtracker`, `database.generation=update`, CORS enabled
- [ ] Gate build passa (package skipTests) via host ou Docker

**Tests**: none
**Gate**: build
**Commit**: `build(backend): scaffold Quarkus Maven project with OIDC and Postgres`

---

### T2: Entidades JPA User, Racket, PlaySession

**What**: Entidades com anotações Hibernate e relacionamentos
**Where**: `backend/src/main/java/br/com/stringtracker/model/`
**Depends on**: T1
**Requirement**: BE-03, BE-04, BE-05

**Done when**:
- [ ] User com id, keycloakId, name, email, isPremium
- [ ] Racket ManyToOne User + campos do spec
- [ ] PlaySession ManyToOne Racket + durationMinutes, datePlayed
- [ ] Gate build passa

**Tests**: none
**Gate**: build
**Commit**: `feat(backend): add JPA entities User Racket PlaySession`

---

### T3: Repositórios Panache

**What**: UserRepository, RacketRepository, PlaySessionRepository
**Where**: `backend/src/main/java/br/com/stringtracker/repository/`
**Depends on**: T2
**Requirement**: BE-06

**Done when**:
- [ ] Três repos implementam `PanacheRepository<Entity>`
- [ ] UserRepository com find por keycloakId
- [ ] Gate build passa

**Tests**: none
**Gate**: build
**Commit**: `feat(backend): add Panache repositories`

---

### T4: DTOs + CurrentUserService (JIT)

**What**: DTOs request/response + serviço que resolve/cria User a partir do JWT
**Where**: `dto/`, `service/CurrentUserService.java`
**Depends on**: T3
**Requirement**: BE-07, AD-003

**Done when**:
- [ ] DTOs com Bean Validation
- [ ] JIT cria User com isPremium=false
- [ ] Testes unitários/Quarkus do JIT e regras auxiliares conforme matrix
- [ ] Gate quick passa

**Tests**: unit (`@QuarkusTest` onde necessário)
**Gate**: quick
**Commit**: `feat(backend): add DTOs and JIT CurrentUserService`

---

### T5: RacketResource GET/POST + limite free

**What**: Endpoints `/api/rackets` com auth e regra 403
**Where**: `resource/RacketResource.java` + testes
**Depends on**: T4
**Requirement**: BE-08, BE-09

**Done when**:
- [ ] GET lista raquetes do user autenticado
- [ ] POST cria; free com ≥1 → 403 + mensagem exata
- [ ] Testes cobrem happy + 403
- [ ] Gate full passa

**Tests**: integration `@QuarkusTest`
**Gate**: full
**Commit**: `feat(backend): add RacketResource with free tier limit`

---

### T6: PlaySessionResource POST + acumulação de horas

**What**: `POST /api/sessions` persiste sessão e atualiza `totalHoursPlayed`
**Where**: `resource/PlaySessionResource.java` + testes
**Depends on**: T5
**Requirement**: BE-10

**Done when**:
- [ ] Sessão salva; horas += durationMinutes/60.0 em transação
- [ ] Response 201 com `racketTotalHoursPlayed` (api-v1)
- [ ] 404 se raquete inexistente ou de outro user
- [ ] Testes cobrem happy + 404 + hours
- [ ] Gate full passa

**Tests**: integration `@QuarkusTest`
**Gate**: full
**Commit**: `feat(backend): add PlaySessionResource and hours accumulation`

---

### T7: Realm Keycloak export + README backend

**What**: Realm JSON demo + README com como subir compose e obter token
**Where**: `backend/keycloak/realm-stringtracker.json`, `backend/README.md`
**Depends on**: T6
**Requirement**: BE-01 (ops)

**Done when**:
- [ ] Realm com client `stringtracker-api` e users demo free/premium
- [ ] README com passos locais
- [ ] Gate build passa

**Tests**: none
**Gate**: build
**Commit**: `docs(backend): add Keycloak realm and local setup README`

---

## Phase Execution Map

```
Phase 1 → Phase 2

Phase 1:  T1 ──→ T2 ──→ T3
Phase 2:  T4 ──→ T5 ──→ T6 ──→ T7
```

## Task Granularity Check

| Task | Scope | Status |
| ---- | ----- | ------ |
| T1 | scaffold/config | ✅ |
| T2 | entities package | ✅ cohesive |
| T3 | repos package | ✅ |
| T4 | dto + service | ✅ cohesive |
| T5 | one resource + tests | ✅ |
| T6 | one resource + tests | ✅ |
| T7 | realm + docs | ✅ |

## Diagram-Definition Cross-Check

| Task | Depends On (body) | Diagram | Status |
| ---- | ----------------- | ------- | ------ |
| T1 | None | start | ✅ |
| T2 | T1 | T1→T2 | ✅ |
| T3 | T2 | T2→T3 | ✅ |
| T4 | T3 | T3→T4 | ✅ |
| T5 | T4 | T4→T5 | ✅ |
| T6 | T5 | T5→T6 | ✅ |
| T7 | T6 | T6→T7 | ✅ |

## Test Co-location Validation

| Task | Layer | Matrix | Task Says | Status |
| ---- | ----- | ------ | --------- | ------ |
| T1 | config | none | none | ✅ |
| T2 | entity | none | none | ✅ |
| T3 | repository | none (build) | none | ✅ |
| T4 | domain service | unit | unit | ✅ |
| T5 | REST | e2e/integration | integration | ✅ |
| T6 | REST | e2e/integration | integration | ✅ |
| T7 | docs | none | none | ✅ |
