# Backend Quarkus Bootstrap Validation

**Date**: 2026-07-12
**Spec**: `.specs/features/backend-bootstrap/spec.md`
**Contract**: `.specs/contracts/api-v1.md`
**Diff range**: `7f63c25..a794535` (backend; includes fix `a794535`)
**Verifier**: independent sub-agent (author ≠ verifier)
**Iteration**: 2 (re-verify after FAIL)

---

## Task Completion

| Task | Status | Notes |
| ---- | ------ | ----- |
| T1 | ✅ Done | `7f63c25` |
| T2 | ✅ Done | `8d2615b` |
| T3 | ✅ Done | `0877d19` |
| T4 | ✅ Done | `cf9b581` |
| T5 | ✅ Done | `f49ad49` |
| T6 | ✅ Done | `b527e88` |
| T7 | ✅ Done | `04616c9` |
| Fix (BE-09 + duration edge) | ✅ Done | `a794535` — literal 403 + `durationMinutes: 0` → 400 |

---

## Spec-Anchored Acceptance Criteria

### P1: Bootstrap do projeto Quarkus

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --------- | -------------------- | ----------------------- | ------ |
| pom com extensões pedidas | rest-jackson, panache, jdbc-postgresql, smallrye-jwt (+ oidc) | matrix: none — inspected `pom.xml:41-60` | ⚠️ Spec-precision gap (no automated test; matrix exempt) |
| `database.generation=update` | property `update` | matrix: none — `application.properties:8` | ⚠️ Spec-precision gap (matrix exempt) |
| CORS habilitado | CORS enabled | matrix: none — `application.properties:15-18` | ⚠️ Spec-precision gap (matrix exempt) |
| packages model/repository/resource/dto | package tree | matrix: none — tree under `br.com.stringtracker` | ⚠️ Spec-precision gap (matrix exempt) |

### P1: Domínio JPA e repositórios

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --------- | -------------------- | ----------------------- | ------ |
| User mapeado | id, name, email, isPremium (+ keycloakId) | `CurrentUserServiceTest.java:37-43` — id/keycloakId/`isPremium==false`/email/name | ✅ PASS |
| Racket mapeado | ManyToOne + campos spec | `RacketResourceTest.java:63-68` — brand/model/stringModel/tensionLbs/dateStrung/totalHoursPlayed | ✅ PASS |
| PlaySession mapeado | ManyToOne + durationMinutes + datePlayed | `PlaySessionResourceTest.java:68-70` | ✅ PASS |
| Repos Panache | três `PanacheRepository` | matrix: none — inspected `*Repository.java` | ⚠️ Spec-precision gap (matrix exempt) |

### P1: API de raquetes com limite free

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --------- | -------------------- | ----------------------- | ------ |
| GET /api/rackets → lista do user | 200 + lista filtrada | `RacketResourceTest.java:45-46` — `statusCode(200)`, `hasSize(0)`; `:123-124` — `hasSize(2)` | ✅ PASS |
| POST Premium → 201 | status 201 | `RacketResourceTest.java:110`, `:117` — `statusCode(201)` | ✅ PASS |
| POST free ≥1 → 403 + mensagem **exata** | 403 + texto plano = `Limite de raquetes atingido para usuários gratuitos. Faça o upgrade para o Premium!` | `RacketResourceTest.java:91-94` — `statusCode(403)`, `contentType("text/plain")`, `body(equalTo("<literal spec string>"))` | ✅ PASS (fixed in `a794535`) |
| POST free sem raquetes → 201 | 201 + fields | `RacketResourceTest.java:62-68` | ✅ PASS |

### P1: Registro de sessão e acumulação de horas

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --------- | -------------------- | ----------------------- | ------ |
| POST /api/sessions → persiste PlaySession | 201 + sessão ligada | `PlaySessionResourceTest.java:67-70` | ✅ PASS |
| horas `+= durationMinutes/60.0` | 90 min → 1.5 | `PlaySessionResourceTest.java:71` — `racketTotalHoursPlayed` = `1.5f`; `:77` | ✅ PASS |
| raquete inexistente / outro user → 404 | status 404 | `PlaySessionResourceTest.java:99`, `:123` | ✅ PASS |

**Status**: ✅ Behavioral ACs covered; ⚠️ structural ACs matrix-exempt (no automated tests)

---

## Discrimination Sensor

Scratch copy (no `target/`; UTF-8 mutation). Real tree untouched.

| Mutation | File:line | Description | Killed? |
| -------- | --------- | ----------- | ------- |
| 1 | `RacketResource.java:29-30` | `FREE_TIER_LIMIT_MESSAGE` → wrong text | ✅ Killed — expected literal, got wrong body (`MUT1_EXIT=1`) |
| 2 | `PlaySessionResource.java:54` | `/ 60.0` → `/ 30.0` | ✅ Killed — expected 1.5F, actual 3.0F |
| 3 | `PlaySessionResource.java:44` | ownership check removed | ✅ Killed — expected 404, was 201 |

**Sensor depth**: lightweight (3 targeted)
**Result**: 3/3 killed — PASS ✅

> Note: first re-verify attempt with PowerShell `-replace` falsely reported mutant#1 survival (encoding/replace miss). Confirmed kill with UTF-8 `String.Replace` + clean compile.

---

## Interactive UAT Results

N/A — backend-only.

---

## Code Quality

| Principle | Status |
| --------- | ------ |
| Minimum code | ✅ |
| Surgical changes | ✅ |
| No scope creep | ✅ |
| Matches patterns | ✅ |
| Spec-anchored outcome check | ✅ (403 literal anchored) |
| Per-layer Coverage Expectation | ✅ for REST happy+edge+error in scope; residual: transactional rollback untested (Minor, accepted for bootstrap) |
| Every test maps to a spec requirement | ✅ |
| Documented guidelines followed | ✅ none for Java — strong defaults (`tasks.md` matrix) |

---

## Edge Cases

- [x] Sem auth → 401 (`RacketResourceTest.java:132`) — JWT supersedes `X-User-Id` edge wording
- [x] `durationMinutes <= 0` → 400 (`PlaySessionResourceTest.java:153` — `statusCode(400)` with `durationMinutes: 0`)
- [x] Lista vazia → `[]` 200 (`RacketResourceTest.java:45-46`)
- [ ] Mid-failure session+hours `@Transactional` rollback — annotation present (`PlaySessionResource.java:39`); **no dedicated rollback test** (Minor residual, not a FAIL for bootstrap)

---

## Gate Check

- **Gate command**: `mvn test` in `backend/` (JAVA_HOME=IntelliJ JBR; `-Dmaven.repo.local=.../.m2`)
- **Result**: 11 passed, 0 failed, 0 skipped
  - PlaySessionResourceTest: 4
  - RacketResourceTest: 5
  - CurrentUserServiceTest: 2
- **Test count before feature**: 0
- **Test count after feature / after fix**: 11
- **Delta vs prior validation**: +1 (`createSession_nonPositiveDuration_returns400`)
- **Skipped**: none
- **Failures**: none

---

## Fix Plans

None for FAIL blockers. Optional residual:

### Residual (Minor): transactional rollback test

- Accepted for bootstrap scope unless team wants P0-level confidence.

---

## Requirement Traceability Update

| Requirement | Previous (iter 1) | New Status |
| ----------- | ----------------- | ---------- |
| BE-01..BE-02 | ⚠️ structural untested | ⚠️ Implemented (matrix exempt) |
| BE-03..BE-08 | ✅ / mixed | ✅ Verified |
| BE-09 | ❌ Needs Fix | ✅ Verified |
| BE-10 | ✅ + edge 400 open | ✅ Verified (incl. 400 edge) |
| BE-11 | ✅ | ✅ Verified |
| BE-12 | ✅ | ✅ Verified |

---

## Summary

**Overall**: ✅ Ready

**Spec-anchored check**: behavioral ACs matched (incl. BE-09 literal + duration≤0); ⚠️ structural matrix-exempt gaps only
**Sensor**: 3/3 mutations killed
**Gate**: 11 passed

**What works**: Free-tier 403 exact message (literal-anchored), non-positive duration 400, hours `/60.0`, ownership 404, racket CRUD-lite, JIT user, gate green.

**Issues found**: None blocking. Minor residual: no transactional rollback test.

**Next steps**: Feature validation complete; optional follow-up for rollback test only.
