# MVP Shell Validation

**Date**: 2026-07-12
**Spec**: `.specs/features/mvp-shell/spec.md`
**Diff range**: `29f2879..HEAD` (frontend commits `88a0cd7`…`4d9c5b8`; backend ignored)
**Verifier**: independent sub-agent (author ≠ verifier)

---

## Task Completion

| Task | Status | Notes |
| ---- | ------ | ----- |
| T1 NativeWind | ✅ Done | Commit `88a0cd7`; configs present |
| T2 racketStore + tests | ✅ Done | Commit `0f5111b`; 6 unit tests |
| T3 WearProgress | ✅ Done | Commit `1e41a9c` |
| T4 Tabs | ✅ Done | Commit `f13e283`; `two.tsx` ausente |
| T5 Home | ✅ Done | Commit `c34ca85` |
| T6 Raquetes | ✅ Done | Commit `d798ee7` |
| T7 Perfil + Paywall | ✅ Done | Commit `252008e` |
| T8 API stub | ✅ Done | Commit `4d9c5b8` |

---

## Spec-Anchored Acceptance Criteria

### P1: NativeWind configurado (MVP-01)

| Criterion (WHEN X THEN Y) | Spec-defined outcome | `file:line` + assertion | Result |
| ------------------------- | -------------------- | ----------------------- | ------ |
| WHEN projeto inicia Metro THEN estilos NativeWind sem erro de config | Configs `tailwind`/`metro`/`babel`/`global.css` presentes e integrados | Presença: `tailwind.config.js:1-20`, `metro.config.js:1-6`, `babel.config.js:1-9`, `global.css`, `app/_layout.tsx:8` (`import '../global.css'`); gate `npx tsc --noEmit` exit 0 | ⚠️ PASS (build/tsc + code presence; sem assertion automatizada de Metro/runtime) |
| WHEN componente usa `className` Tailwind válido THEN estilo aplicado | UI usa `className` NativeWind | Presença: ex. `app/(tabs)/index.tsx:40`, `components/WearProgress.tsx:21-38`, `app/paywall.tsx:21`; sem unit/e2e visual | ⚠️ PASS (code presence only; matrix: Tests none) |

### P1: Navegação por Tabs (MVP-02)

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --------- | -------------------- | ----------------------- | ------ |
| WHEN app abre THEN tabs Home, Minhas Raquetes, Perfil | Títulos exatos das 3 tabs | Presença: `app/(tabs)/_layout.tsx:21`, `:34`, `:48` (`title: 'Home'`, `'Minhas Raquetes'`, `'Perfil'`) | ⚠️ PASS (UI; sem unit) |
| WHEN toca cada tab THEN navega `index`/`raquetes`/`perfil` | Screens registradas com esses `name` | Presença: `_layout.tsx:19` `index`, `:32` `raquetes`, `:45` `perfil` + arquivos de tela | ⚠️ PASS (UI; sem unit) |
| WHEN `_layout` renderiza THEN não expor "Tab Two" | Sem rota `two` | `Test-Path app/(tabs)/two.tsx` → False; sem match `two`/`Tab Two` em `_layout.tsx` | ⚠️ PASS (code absence + presence) |

### P1: Home com desgaste (MVP-03, MVP-04)

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --------- | -------------------- | ----------------------- | ------ |
| WHEN Home monta com seed THEN progresso circular 12h de 30h | Gauge circular + texto horas | UI: `WearProgress.tsx:20-39` (`rounded-full`, `{displayHours}h de {maxHours}h`); Home: `index.tsx:57-60`; domínio: `racketStore.test.ts:20-33` — `totalHoursPlayed: 12`, `DEFAULT_MAX_HOURS` → `30` | ⚠️ PASS (domínio unit ✅; UI presence only) |
| WHEN Home monta THEN marca/modelo corda, libras, data | Luxilon / 52 lbs / data | UI: `index.tsx:64-69`; seed: `racketStore.ts:18-22` | ⚠️ PASS (UI presence; seed unit cobre string/tension, não `dateStrung`) |
| WHEN toca "+" THEN +1h na corda ativa e atualiza progresso | Confirma Alert → `addTrainingHour` → +1 | UI wiring: `index.tsx:23-35`; domínio: `racketStore.test.ts:35-41` — `expect(...totalHoursPlayed).toBe(13)` | ⚠️ PASS (domínio ✅; UI Confirm path sem unit) |
| WHEN sem raquete ativa THEN empty state | Orientar cadastrar raquete; sem crash | UI: `index.tsx:38-48`; domínio: `racketStore.test.ts:74-81` — `expect(getActiveRacket()).toBeNull()` | ⚠️ PASS (domínio null ✅; copy UI presence) |

### P1: Lista Minhas Raquetes (MVP-05)

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --------- | -------------------- | ----------------------- | ------ |
| WHEN aba abre THEN lista ≥ seed Babolat Pure Drive | Lista em memória com seed | UI: `raquetes.tsx:79-100`; domínio: `racketStore.test.ts:20-28` — `brand: 'Babolat'`, `model: 'Pure Drive'` | ⚠️ PASS (domínio ✅; UI list presence) |
| WHEN toca adicionar THEN inclui nova raquete | Nova raquete no estado (design: free limit bloqueia 2ª) | Free: `racketStore.test.ts:43-58` — `error: 'FREE_LIMIT'`, length 1; Premium: `:60-72` — length 2; UI: `raquetes.tsx:12-74` | ⚠️ Spec-precision: AC omite limite free; design/tasks/BE cobrem — implementação alinhada ao design |

### P1: Perfil com CTA Premium (MVP-06)

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --------- | -------------------- | ----------------------- | ------ |
| WHEN Perfil abre THEN opções básicas placeholder | Conta / notificações ou similar | Presença: `perfil.tsx:14-18` (Plano, Raquetes, Notificações, Conta) | ⚠️ PASS (UI presence only) |
| WHEN toca "Seja Premium" THEN navega modal `paywall` | Link `/paywall` | Presença: `perfil.tsx:21-25` (`href="/paywall"`, texto Seja Premium); stack: `app/_layout.tsx:53-56` | ⚠️ PASS (UI presence only) |

### P1: Paywall modal (MVP-07)

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --------- | -------------------- | ----------------------- | ------ |
| WHEN paywall abre THEN `slate-900` + 3 benefícios | Fundo escuro; Múltiplas raquetes; Sync; Gráficos | Presença: `paywall.tsx:6-10`, `:21`, `:28-33` | ⚠️ PASS (UI presence only) |
| WHEN paywall abre THEN Mensal R$9,90 vs Anual R$79,90 + 7 dias grátis | Seletor; anual default | Presença: `paywall.tsx:13` `useState('yearly')`; `:40-50` preços e subtitle | ⚠️ PASS (UI presence only) |
| WHEN CTA checkout THEN feedback local sem pagamento real | Alert “Checkout simulado” | Presença: `paywall.tsx:15-17`, `:54-57` | ⚠️ PASS (UI presence only) |
| WHEN rota registrada THEN `paywall` modal no root stack | `presentation: 'modal'` | Presença: `app/_layout.tsx:53-56` | ⚠️ PASS (UI presence only) |

### P1: Hook `useRackets` com seed (MVP-08)

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --------- | -------------------- | ----------------------- | ------ |
| WHEN hook/store inicializa THEN seed Babolat/Luxilon/52/12h/max30 | Valores exatos | `racketStore.test.ts:20-33` — `toMatchObject({ brand, model, stringModel, tensionLbs: 52, totalHoursPlayed: 12 })`; `DEFAULT_MAX_HOURS` → `30` | ✅ PASS |
| WHEN `addTrainingHour` THEN uso aumenta e consumidores re-renderizam | +1h; listeners/re-render | `racketStore.test.ts:35-41` — `toBe(13)`; emit em `racketStore.ts:36-42`, hook `useRackets.ts:14` | ⚠️ PASS parcial: +1h ✅; **sem assertion de `subscribe`/re-render** |
| WHEN `addRacket` THEN lista inclui nova (se permitido) | Premium adiciona; free FREE_LIMIT | `racketStore.test.ts:43-72` | ✅ PASS (com regra free do design) |
| Types Racket / StringSetup equiv. / horas | TS strict | `hooks/types.ts:5-28`; gate `tsc --noEmit` exit 0 | ✅ PASS (types + tsc; “StringSetup” = campos em `Racket`) |

**Status**: ⚠️ Spec-precision / UI-coverage gaps flagged; domínio core com evidência unit — **nenhum AC domínio sem evidência de outcome principal**

---

## Discrimination Sensor

Scratch: backup `hooks/racketStore.ts` → `%TEMP%\racketStore.mvp-shell.backup.ts`; mutar → `npm test` → restaurar. Hash pós-restore idêntico ao backup. Working tree do store limpo; `npm test` final 6/6 pass.

| Mutation | File:line | Description | Killed? |
| -------- | --------- | ----------- | ------- |
| 1 | `hooks/racketStore.ts:77` | `+ 1` → `+ 2` em `addTrainingHour` | ✅ Killed |
| 2 | `hooks/racketStore.ts:90` | `message: FREE_RACKET_LIMIT_MESSAGE` → `'wrong limit message'` | ✅ Killed |
| 3 | `hooks/racketStore.ts:122` | Removido `Math.min(1, …)` (clamp superior) | ✅ Killed |

**Sensor depth**: lightweight (3 behavior-level mutations)
**Result**: 3/3 killed — PASS ✅

---

## Interactive UAT Results

Não executado nesta verificação (Verifier automatizado / read-only). Feature é user-facing — UAT humano ainda recomendado para tabs/NativeWind visual.

| # | Test | Result | Details |
| - | ---- | ------ | ------- |
| — | UAT interativo | ⏭️ Skip | Fora do escopo deste Verifier agent |

---

## Code Quality

| Principle | Status |
| --------- | ------ |
| Minimum code | ✅ |
| Surgical changes | ✅ (frontend scoped; backend ignored) |
| No scope creep | ✅ |
| Matches patterns | ✅ (Expo Router + NativeWind + store module) |
| Spec-anchored outcome check | ⚠️ UI ACs só presence; domínio alinhado |
| Per-layer Coverage Expectation | ✅ domain unit 1:1 outcomes; UI = build per matrix |
| Every test maps to spec AC / edge | ✅ (seed, +1h, FREE_LIMIT, premium add, empty active, clamp) |
| Documented guidelines | ✅ `AGENTS.md` Expo v57; matrix “strong defaults” |

---

## Edge Cases

- [x] `hoursUsed` ≥ `maxHours`: clamp visual 100% — `wearProgressRatio` + `racketStore.test.ts:83-88` (`45/30` → `1`); UI `WearProgress` usa ratio
- [x] Lista vazia / sem ativa: Home empty — `index.tsx:38-48`; `getActiveRacket` null test
- [x] Cancelar Alert do "+": `index.tsx:27` `Cancelar` sem `onPress` mutador — **só code presence; sem unit**
- [x] Paywall fecha sem alterar premium: `paywall.tsx` não chama `setPremium`; `isPremium` permanece mock — **só code presence; sem unit**

---

## Gate Check

- **Gate command**: `npm test && npx tsc --noEmit` (Full / Build)
- **Result**: `npm test` — **6 passed, 0 failed, 0 skipped**; `npx tsc --noEmit` — **exit 0**
- **Test count before feature**: 0 (sem suite no template Expo pré-T2)
- **Test count after feature**: 6
- **Delta**: +6
- **Skipped tests**: none
- **Failures**: none

---

## Fix Plans (if issues found)

### Fix 1 (Minor, opcional): Assert `subscribe` notifica após mutação

- **Root cause**: AC MVP-08 menciona re-render de consumidores; testes cobrem só delta de horas, não o contrato `subscribe`/`emit`
- **Fix task**: Em `hooks/__tests__/racketStore.test.ts`, registrar listener via `subscribe`, chamar `addTrainingHour`, `expect(listener).toHaveBeenCalled()`
- **Priority**: Minor (não bloqueia outcome +1h)

### Fix 2 (Cosmetic / UAT): Confirmar NativeWind visual e fluxo tabs/paywall

- **Root cause**: Matrix dispensa e2e; Metro/className não têm assertion runtime
- **Fix task**: UAT humano (cold start Home 12/30, 3 tabs, Seja Premium → modal, CTA Alert)
- **Priority**: Cosmetic/process (esperado)

### Fix 3 (Spec hygiene): AC Minhas Raquetes vs free limit

- **Root cause**: Spec AC “adicionar inclui nova raquete” sem exceção free; design bloqueia
- **Fix task**: Atualizar spec AC para “THEN inclui nova **ou** mostra limite free alinhado ao BE”
- **Priority**: Minor (docs)

---

## Requirement Traceability Update

| Requirement | Previous Status | New Status |
| ----------- | --------------- | ---------- |
| MVP-01 | Verified (author) | ✅ Verified (independent; UI gate only) |
| MVP-02 | Verified (author) | ✅ Verified (independent; UI presence) |
| MVP-03 | Verified (author) | ✅ Verified (domínio + UI presence) |
| MVP-04 | Verified (author) | ✅ Verified (domínio unit + UI wiring presence) |
| MVP-05 | Verified (author) | ✅ Verified (com free-limit per design) |
| MVP-06 | Verified (author) | ✅ Verified (UI presence) |
| MVP-07 | Verified (author) | ✅ Verified (UI presence) |
| MVP-08 | Verified (author) | ✅ Verified (unit; re-render parcialmente) |

---

## Summary

**Overall**: ✅ Ready (com ⚠️ esperados em UI e 1 gap Minor opcional no sensor de subscribe)

**Spec-anchored check**: Outcomes domínio principais matched; UI ACs = presence + tsc (matrix); 1 spec-precision (free limit); 1 partial (re-render)
**Sensor**: 3/3 mutations killed
**Gate**: 6 passed, tsc clean

**What works**: Seed tipado, +1h, free limit + mensagem BE, clamp, tabs/Home/Raquetes/Perfil/Paywall presentes, stub API, gates verdes, testes discriminativos.

**Issues found**: Sem UAT visual; sem unit para subscribe/re-render; AC lista vs free limit impreciso na spec.

**Next steps**: Opcional — teste de `subscribe`; UAT humano; alinhar wording do AC de add racket na spec. Sem fix bloqueante obrigatório para marcar feature done.
