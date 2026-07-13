# API Mobile Integration Validation

**Date**: 2026-07-12
**Spec**: `.specs/features/api-mobile-integration/spec.md`
**Diff range**: `4eebaba^..HEAD` (fix commits `f244716`, `d4266df`)
**Verifier**: independent sub-agent (author ≠ verifier)
**Iteration**: re-verify #2 after `test(mobile): cover 404 message, 403 no-append, and 401 reauth helpers`

---

## Task Completion

| Task | Status | Notes |
| ---- | ------ | ----- |
| T1–T8 | ✅ Done | Feature commits intact |
| Fix iter 1 | ✅ | `f244716` — 404 Alert + training DTOs |
| Fix iter 2 | ✅ | `d4266df` — 404 message assert, 403 no-append, 401 reauth, client id |

---

## Spec-Anchored Acceptance Criteria

### P1: Flyway migrations no backend

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --------- | -------------------- | ----------------------- | ------ |
| Flyway migrate-at-start + V1 | migrations applied | `application.properties:9`; gate Flyway v1 log | ✅ PASS |
| `generation=none` | exact `none` | `application.properties:6` / `:30` | ✅ PASS |
| V1 schema User/Racket/PlaySession + FKs | tables + uniques | `V1__create_core_schema.sql:2-34` | ✅ PASS |
| Migration fail → app fail | fail-fast | Platform Quarkus/Flyway default; no project test | ⚠️ Spec-precision / documented assumption |

### P1: Login Keycloak no Expo

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --------- | -------------------- | ----------------------- | ------ |
| Sem token → login OIDC+PKCE `stringtracker-expo` | client + login UI | `authEffects.test.ts:48` — `KEYCLOAK_CLIENT_ID.toBe('stringtracker-expo')`; PKCE/`promptAsync` browser path — no unit harness | ⚠️ UI/browser deferred (impl: `auth.ts` PKCE + `_layout` login gate) |
| Login sucesso → store token + tabs | persist + tabs | `tokenStore.test.ts:11-13` — save/get token; tab switch is Expo Router UI | ⚠️ Partial: store ✅; tabs UI deferred |
| Ausente/`401` → reauth | logout on unauthorized | `authEffects.test.ts:20` — `shouldReauthOnError(unauthorized)===true`; `useRackets.ts:39-40` calls `logout()` | ✅ PASS |
| Logout → clear tokens + login | clear (+ route) | `tokenStore.test.ts:16-19` — `clearAccessToken` → `null`; route login UI | ⚠️ Partial: clear ✅; route UI deferred |

### P1: Cliente HTTP tipado + DTOs

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --------- | -------------------- | ----------------------- | ------ |
| Base URL + Bearer | default + header | `api.test.ts:39-44` | ✅ PASS |
| Types espelham `dtos.md` | wire types | `api.ts:18-35` | ✅ PASS |
| GET rackets tipado/erro | array/error | `api.test.ts:37-38` | ✅ PASS |
| POST bodies contrato (sem `totalHoursPlayed` create) | body exact | `api.test.ts:88-96`, `115-120` | ✅ PASS |

### P1: Substituir mock — listar e criar raquetes

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --------- | -------------------- | ----------------------- | ------ |
| Monta → GET + spinner + lista/`[]` | fetch + spinner UI | Hook `listRackets` + `raquetes.tsx` `ActivityIndicator` — spinner visual sem unit path | ⚠️ UI deferred (impl present; typecheck gate) |
| Add → POST + 201 atualiza UI | append on success | `authEffects.test.ts:40-42` — append length 2 | ✅ PASS |
| `403` literal + NÃO adiciona local | message + no append | `api.test.ts:61-64` literal; `authEffects.test.ts:34-35` — `toEqual([sample])` / `toHaveLength(1)` | ✅ PASS |
| Rede → mensagem; sem cache | network + clear | `api.test.ts:145` — `kind: 'network'`; clear em `useRackets.ts:67` | ⚠️ Partial: kind ✅; clear list unit deferred |
| Mock não é SoT | sem seed store | `racketStore` ausente; SoT = `listRackets` | ✅ PASS (structural) |

### P1: Treino via `POST /api/sessions`

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --------- | -------------------- | ----------------------- | ------ |
| +1h → `durationMinutes: 60` + date ISO | body | `training.test.ts:8-12`; `api.test.ts:115-120` | ✅ PASS |
| `201` → `racketTotalHoursPlayed` | hours from API | `training.test.ts:39-42` | ✅ PASS |
| `404` → raquete não encontrada | exact message | `api.test.ts:133-137` — `message: 'Raquete não encontrada.'`; Home `index.tsx:34` `result.message` | ✅ PASS |
| Em voo → loading / no double-submit | `mutating` gate | `useRackets.ts` + Home `disabled={mutating}` — UI/gesture | ⚠️ UI deferred (impl present) |

### P2: Premium na UI a partir do JWT

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --------- | -------------------- | ----------------------- | ------ |
| Role `premium` → true | true | `jwt.test.ts:20` | ✅ PASS |
| Sem role → false | false | `jwt.test.ts:27` | ✅ PASS |
| Paywall CTA simulado | Alert only, no upgrade API | `paywall.tsx:17-21` — `Alert.alert('Checkout simulado', ...)` | ⚠️ UI deferred (impl clearly Alert-only; no upgrade call) |

**Status**: ✅ All critical ACs covered; ⚠️ documented assumptions for UI/browser-only + Flyway fail-fast

---

## Discrimination Sensor

| Mutation | File:line | Description | Killed? |
| -------- | --------- | ----------- | ------- |
| 1 | `http.ts:70` | 404 message → `'Algo deu errado.'` | ✅ Killed (`api.test.ts:136`) |
| 2 | `authEffects.ts` | 403 failure still appends ghost racket | ✅ Killed (`authEffects.test.ts:34`) |
| 3 | `authEffects.ts:5` | Flip `shouldReauthOnError` unauthorized check | ✅ Killed (`authEffects.test.ts:20`) |

**Sensor depth**: lightweight (fix-surface)
**Result**: 3/3 killed — PASS ✅

---

## Interactive UAT

Not performed (device Keycloak/AuthSession deferred per matrix).

---

## Code Quality

| Principle | Status |
| --------- | ------ |
| Minimum code | ✅ |
| Surgical changes | ✅ |
| No scope creep | ✅ |
| Matches patterns | ✅ |
| Spec-anchored outcome check | ✅ (⚠️ only where unit path absent by design) |
| Per-layer Coverage Expectation | ✅ helpers cover domain ACs; screens typecheck |
| Every test maps to AC / Done-when | ✅ |
| Guidelines: `AGENTS.md`, coding-principles | ✅ |

---

## Edge Cases

- [x] `[]` Home empty — impl (`index.tsx`); ⚠️ untested UI
- [x] `400` validation kind — `kindFromStatus`; client happy-path sends 60
- [x] Token `401` → reauth — `shouldReauthOnError` ✅
- [x] Free 2ª → 403 literal + no append ✅
- [x] Sessions 404 message ✅
- [x] Offline network kind ✅; clear lista ⚠️ partial

---

## Gate Check

- **Gate**: BE `mvn -f backend/pom.xml test`; FE `npm test && npm run typecheck`
- **Result**: BE **16** passed; Mobile **19** passed; typecheck OK
- **Mobile before feature**: 6 → **after re-verify #2**: 19
- **Skipped**: none
- **Failures**: none

---

## Fix Plans

None blocking. Optional follow-ups (non-blocking ⚠️):

1. Document Flyway fail-fast as platform-guaranteed in spec, or add negative migrate test
2. Device UAT: AuthSession login, spinner, paywall Alert (interactive)

---

## Requirement Traceability Update

| Requirement | Prior | New |
| ----------- | ----- | --- |
| AMI-00 | ⚠️ | ⚠️ Verified w/ fail-fast assumption |
| AMI-01 | ❌ | ⚠️ Verified w/ browser UI deferred |
| AMI-02 | ⚠️ | ✅/⚠️ clear+reauth ✅; route UI ⚠️ |
| AMI-03 | ✅ | ✅ Verified |
| AMI-04 | ❌ | ⚠️ spinner UI deferred |
| AMI-05 | ❌ | ✅ Verified |
| AMI-06 | ❌ | ✅ Verified |
| AMI-07 | ✅ | ✅ Verified |
| AMI-08 | ❌ | ✅ Verified (404 message; loading ⚠️) |
| AMI-09 | ✅ | ✅ Verified |
| AMI-10 | ❌ | ⚠️ paywall UI deferred |

---

## Summary

**Overall**: ✅ Ready (PASS) — remaining items are documented ⚠️ assumptions, not ❌ gaps

**Spec-anchored check**: Critical ACs matched; UI/browser-only + Flyway fail-fast flagged ⚠️
**Sensor**: 3/3 killed
**Gate**: BE 16 / mobile 19 + typecheck OK

**Closed in iter 2**: 404 exact message (sensor kill); 403 no-append; 401 `shouldReauthOnError`; `stringtracker-expo` client id

**Next steps**: Optional device UAT; update `spec.md` requirement statuses to Verified; no further fix iteration required for automated verification.
