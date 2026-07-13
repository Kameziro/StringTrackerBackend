# MVP Shell — Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name and follow its Execute flow and Critical Rules.**

**Design**: `.specs/features/mvp-shell/design.md`  
**Status**: Done

---

## Test Coverage Matrix

> Guidelines found: `AGENTS.md` (Expo v57 docs only) — none on testing. Strong defaults applied for domain; UI = build gate (no e2e harness in repo).

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| ---------- | ------------------ | -------------------- | ---------------- | ----------- |
| Domain store (`racketStore`) | unit | ACs seed, +1h, free limit, clamp edge | `hooks/__tests__/*.test.ts` | `npm test` |
| UI screens / components | none | — (build gate) | — | `npx tsc --noEmit` |
| NativeWind / Expo config | none | — (build gate) | — | `npx tsc --noEmit` |
| services stub types | none | — (build gate) | — | `npx tsc --noEmit` |

## Gate Check Commands

| Gate Level | When to Use | Command |
| ---------- | ----------- | ------- |
| Quick | After domain unit tasks | `npm test` |
| Full | After UI tasks that also touch store | `npm test && npx tsc --noEmit` |
| Build | Config / screens / last phase task | `npx tsc --noEmit` |

---

## Execution Plan

### Phase 1: Foundation

```
T1 → T2
```

### Phase 2: UI Core

```
T3 → T4 → T5 → T6
```

### Phase 3: Monetization + stub

```
T7 → T8
```

---

## Task Breakdown

### T1: Configurar NativeWind

**What**: Instalar e configurar NativeWind/Tailwind para Expo Router.  
**Where**: `package.json`, `tailwind.config.js`, `babel.config.js`, `metro.config.js`, `global.css`, `nativewind-env.d.ts`, `app/_layout.tsx` (import css)  
**Depends on**: None  
**Reuses**: Expo default entry  
**Requirement**: MVP-01

**Done when**:
- [ ] Dependências NativeWind + tailwindcss instaladas
- [ ] Configs Metro/Babel/Tailwind/CSS presentes
- [ ] `npx tsc --noEmit` passa (ou só erros pré-existentes irrelevantes)

**Tests**: none  
**Gate**: build  
**Commit**: `build(mvp-shell): configure NativeWind for Expo`

---

### T2: racketStore + useRackets com seed e testes

**What**: Store em memória tipado + hook + seed Babolat/Luxilon 12h/30h + unit tests.  
**Where**: `hooks/racketStore.ts`, `hooks/useRackets.ts`, `hooks/__tests__/racketStore.test.ts`, jest config/`package.json` scripts  
**Depends on**: T1  
**Reuses**: shape `api-v1` RacketResponse  
**Requirement**: MVP-08, MVP-04 (lógica +1h)

**Done when**:
- [ ] Seed: Babolat Pure Drive, Luxilon Alu Power, 52 lbs, 12h
- [ ] `addTrainingHour` soma +1h
- [ ] Free limit bloqueia 2ª raquete com mensagem alinhada ao BE
- [ ] Testes unitários cobrem seed, +1h, free limit, empty active
- [ ] `npm test` passa

**Tests**: unit  
**Gate**: quick  
**Commit**: `feat(mvp-shell): add in-memory racket store and hook`

---

### T3: Componente WearProgress

**What**: Círculo de progresso horas usadas / max.  
**Where**: `components/WearProgress.tsx`  
**Depends on**: T1  
**Reuses**: NativeWind  
**Requirement**: MVP-03

**Done when**:
- [ ] Aceita `hoursUsed` e `maxHours`
- [ ] Clamp visual em 100%
- [ ] Exibe texto tipo `12h de 30h`
- [ ] `npx tsc --noEmit` passa

**Tests**: none  
**Gate**: build  
**Commit**: `feat(mvp-shell): add WearProgress circle component`

---

### T4: Tabs Home / Minhas Raquetes / Perfil

**What**: Layout de tabs com 3 abas; remover `two`.  
**Where**: `app/(tabs)/_layout.tsx`, delete `app/(tabs)/two.tsx`, stubs mínimos das rotas se necessário  
**Depends on**: T1  
**Reuses**: SymbolView pattern  
**Requirement**: MVP-02

**Done when**:
- [ ] Tabs: Home, Minhas Raquetes, Perfil
- [ ] Sem tab Two
- [ ] `npx tsc --noEmit` passa

**Tests**: none  
**Gate**: build  
**Commit**: `feat(mvp-shell): replace template tabs with Home Raquetes Perfil`

---

### T5: Tela Home

**What**: Home com WearProgress, detalhes da corda, botão + treino rápido.  
**Where**: `app/(tabs)/index.tsx`  
**Depends on**: T2, T3, T4  
**Reuses**: useRackets, WearProgress  
**Requirement**: MVP-03, MVP-04

**Done when**:
- [ ] Mostra seed 12/30h e detalhes Luxilon/52 lbs/data
- [ ] + confirma e chama `addTrainingHour`
- [ ] Empty state se sem raquete ativa
- [ ] `npx tsc --noEmit` passa

**Tests**: none  
**Gate**: build  
**Commit**: `feat(mvp-shell): implement Home wear dashboard`

---

### T6: Tela Minhas Raquetes

**What**: Lista raquetes + botão adicionar (respeitando limite free).  
**Where**: `app/(tabs)/raquetes.tsx`  
**Depends on**: T2, T4  
**Reuses**: useRackets  
**Requirement**: MVP-05

**Done when**:
- [ ] Lista seed
- [ ] Add cria raquete (ou Alert de limite free)
- [ ] `npx tsc --noEmit` passa

**Tests**: none  
**Gate**: build  
**Commit**: `feat(mvp-shell): implement rackets list screen`

---

### T7: Perfil + Paywall modal

**What**: Perfil com Seja Premium; paywall dark com planos e CTA.  
**Where**: `app/(tabs)/perfil.tsx`, `app/paywall.tsx`, `app/_layout.tsx` (Stack.Screen modal), remover `app/modal.tsx` se substituído  
**Depends on**: T4  
**Reuses**: Expo Router modal presentation  
**Requirement**: MVP-06, MVP-07

**Done when**:
- [ ] Seja Premium → `/paywall`
- [ ] Fundo slate-900; benefícios listados; Mensal/Anual; Anual default; CTA Alert simulado
- [ ] `npx tsc --noEmit` passa

**Tests**: none  
**Gate**: build  
**Commit**: `feat(mvp-shell): add profile tab and premium paywall modal`

---

### T8: Stub services + limpeza rota template

**What**: `services/api.ts` com types do contrato; garantir stack limpa.  
**Where**: `services/api.ts`, `app/_layout.tsx` se ainda referenciar modal antigo  
**Depends on**: T7  
**Reuses**: `.specs/contracts/api-v1.md`  
**Requirement**: MVP-01..08 (fecho estrutural)

**Done when**:
- [ ] Types exportados alinhados ao contrato
- [ ] `API_BASE_URL = http://localhost:8080`
- [ ] `npx tsc --noEmit` passa
- [ ] `npm test` passa

**Tests**: none (types only) + full gate  
**Gate**: full  
**Commit**: `chore(mvp-shell): add Quarkus API stub types`

---

## Phase Execution Map

```
Phase 1 → Phase 2 → Phase 3

Phase 1:  T1 ──→ T2
Phase 2:  T3 ──→ T4 ──→ T5 ──→ T6
Phase 3:  T7 ──→ T8
```

## Task Granularity Check

| Task | Scope | Status |
| ---- | ----- | ------ |
| T1 NativeWind | config set | ✅ cohesive |
| T2 store+hook+tests | domain file set | ✅ cohesive |
| T3 WearProgress | 1 component | ✅ |
| T4 tabs layout | 1 layout + delete | ✅ |
| T5 Home | 1 screen | ✅ |
| T6 Raquetes | 1 screen | ✅ |
| T7 Perfil+Paywall | 2 screens same story | ✅ cohesive |
| T8 services stub | 1 file | ✅ |

## Diagram-Definition Cross-Check

| Task | Depends On (body) | Diagram Shows | Status |
| ---- | ----------------- | ------------- | ------ |
| T1 | None | — | ✅ |
| T2 | T1 | T1→T2 | ✅ |
| T3 | T1 | T3 after T1 (phase2; depends T1 ok) | ✅ |
| T4 | T1 | T4 after T1 | ✅ |
| T5 | T2,T3,T4 | T5 after those | ✅ |
| T6 | T2,T4 | T6 after those | ✅ |
| T7 | T4 | T7 after T4 | ✅ |
| T8 | T7 | T7→T8 | ✅ |

## Test Co-location Validation

| Task | Layer | Matrix Requires | Task Says | Status |
| ---- | ----- | --------------- | --------- | ------ |
| T1 | config | none | none | ✅ |
| T2 | domain store | unit | unit | ✅ |
| T3 | UI component | none | none | ✅ |
| T4 | UI layout | none | none | ✅ |
| T5 | UI screen | none | none | ✅ |
| T6 | UI screen | none | none | ✅ |
| T7 | UI screens | none | none | ✅ |
| T8 | stub types | none | none + full gate | ✅ |
