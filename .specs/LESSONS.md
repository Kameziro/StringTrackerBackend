# LESSONS — auto-maintained by scripts/lessons.py

> Machine-owned. Do NOT hand-edit. Changes are overwritten on the next `lessons.py` write.
> Canonical state lives in `.specs/lessons.json`. Edit lessons only via the script.
> promote_threshold=2 distinct features · window_days=45 · quarantine_threshold=2

## Confirmed (load these at Specify/Design)

Corroborated across multiple features. Safe to apply as guidance.

_none_

## Candidates (under observation — do NOT load as guidance yet)

Seen once or not yet corroborated. Tracked, not trusted.

### L-001 — Assert exact expected error message literals from the spec, not equality to a production constant that can drift with the bug.
- signal: `surviving_mutant` · recurrence: 1 feature(s) · scope: `backend/tests` · harmful: 0
- features: backend-bootstrap
- evidence: RacketResourceTest.java:93 mutant#1 FREE_TIER_LIMIT_MESSAGE (backend/tests)
- last seen: 2026-07-13T01:23:24Z

### L-002 — Cover Bean Validation rejection paths with HTTP assertions (status 400), not only happy-path payloads.
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `backend/rest` · harmful: 0
- features: backend-bootstrap
- evidence: spec edge: durationMinutes <= 0 → 400 (backend/rest)
- last seen: 2026-07-13T01:23:25Z

### L-003 — When a spec mandates an exact error body, the test must pin that exact string so message regressions fail CI.
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `backend/tests` · harmful: 0
- features: backend-bootstrap
- evidence: BE-09 free-tier 403 exact message (backend/tests)
- last seen: 2026-07-13T01:23:26Z

### L-004 — When mapping API errors to UI, assert the user-visible message equals the ApiError/spec text — a generic Alert does not satisfy a specific status outcome
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `mobile/ui-errors` · harmful: 0
- features: api-mobile-integration
- evidence: index.tsx:33-34 / AMI-08 404 (mobile/ui-errors)
- last seen: 2026-07-13T02:22:25Z

### L-005 — Assert request bodies and response-driven state updates in hook/API unit tests — implementation-only wiring is not evidence-or-zero coverage
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `mobile/hooks` · harmful: 0
- features: api-mobile-integration
- evidence: useRackets.ts:125-134 / AMI-07 (mobile/hooks)
- last seen: 2026-07-13T02:22:25Z

### L-006 — Prefer a single source for error message selection; redundant ternary vs fallback paths produce equivalent mutants that inflate sensor noise
- signal: `surviving_mutant` · recurrence: 1 feature(s) · scope: `mobile/services/http` · harmful: 0
- features: api-mobile-integration
- evidence: http.ts:76 equivalent ternary (mobile/services/http)
- last seen: 2026-07-13T02:22:25Z

### L-007 — If an AC relies on framework fail-fast defaults, either add an explicit test or mark the outcome as platform-guaranteed in the spec
- signal: `spec_precision_gap` · recurrence: 1 feature(s) · scope: `backend/flyway` · harmful: 0
- features: api-mobile-integration
- evidence: Flyway AC4 fail-fast (backend/flyway)
- last seen: 2026-07-13T02:22:25Z

### L-008 — Auth gate and logout acceptance criteria need at least one unit assertion on session clear or route-gate decision — typecheck alone is not evidence-or-zero
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `mobile/auth` · harmful: 0
- features: api-mobile-integration
- evidence: AuthContext / AMI-01 AMI-02 (mobile/auth)
- last seen: 2026-07-13T02:22:33Z

### L-009 — Assert exact ApiError.message for status-mapped user copy — kind/status alone lets message regressions survive mutation
- signal: `surviving_mutant` · recurrence: 1 feature(s) · scope: `mobile/services` · harmful: 0
- features: api-mobile-integration
- evidence: http.ts:70 / api.test 404 kind-only (mobile/services)
- last seen: 2026-07-13T02:26:24Z

### L-010 — When UI surfaces ApiError.message, pair it with a unit assertion on the exact fallback string for that HTTP status
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `mobile/ui-errors` · harmful: 0
- features: api-mobile-integration
- evidence: AMI-08 404 message re-verify#1 (mobile/ui-errors)
- last seen: 2026-07-13T02:26:24Z

## Quarantined (failed when applied — ignore)

A confirmed lesson that recurred alongside failure. Kept for the maintainer to review.

_none_
