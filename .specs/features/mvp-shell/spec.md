# MVP Shell — StringTracker Specification

## Problem Statement

Tenistas perdem a noção de quanto jogaram com a mesma corda e trocam tarde demais (quebra/perda de tensão). O StringTracker precisa de um shell de MVP navegável — Home com desgaste, lista de raquetes, perfil/premium e paywall — com dados mock realistas, para validar o fluxo visual antes de plugar Supabase.

## Goals

- [ ] App abre em tabs (Home, Minhas Raquetes, Perfil) estilizado com NativeWind
- [ ] Home exibe progresso de desgaste da corda atual com dados mock (12h / 30h)
- [ ] Perfil navega para paywall modal com planos e CTA de checkout simulado
- [ ] Estado tipado em memória (`useRackets`) pronto para trocar por `/services` (cliente Quarkus, contrato `.specs/contracts/api-v1.md`)

## Out of Scope

| Feature | Reason |
| ------- | ------ |
| Integração real Quarkus/Keycloak | Explicitamente adiada neste shell; mock em memória + contrato em `.specs/contracts/api-v1.md` |
| Integração Supabase | Substituída por Quarkus (AD-004) |
| Checkout / IAP / Stripe reais | CTA simula início; sem pagamento |
| Auth / login | MVP visual sem conta |
| Gráficos de tensão reais | Benefício listado no paywall; não implementado |
| CRUD completo com formulários longos | Fluxos “rápidos” via Alert/prompt mínimos |
| Persistência local (AsyncStorage) | Estado só em memória neste feature |
| Onboarding / splash custom | Template Expo existente basta |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --------------------- | -------------- | --------- | ---------- |
| Visual de progresso na Home | Círculo de progresso (não barra) | Mais “vida útil” / gauge; pedido aceitava ambos | n |
| Vida útil padrão da corda | `maxHours = 30` | Exemplo do brief (12h de 30h) | n |
| Botão "+" na Home | Soma +1h ao uso da corda ativa via `Alert` de confirmação | “Registro rápido” sem tela de formulário | n |
| Adicionar raquete | `Alert.prompt` / fallback com raquete mock adicional | Lista funcional sem form dedicado | n |
| Tema visual geral (tabs) | Light com accent esporte (verde/teal); paywall dark `slate-900` | Paywall explicitamente escuro; resto legível | n |
| Seletor de planos no paywall | Estado local; Anual pré-selecionado (melhor valor + trial) | Destacar oferta anual | n |
| CTA checkout | `Alert` “Checkout simulado” + fechar modal | Sem IAP no escopo | n |
| Raquete seed | Babolat Pure Drive + Luxilon Alu Power @ 52 lbs, 12h/30h, data encordoamento recente | Brief do usuário | n |
| Remover tab `two` e modal template | Substituir por `raquetes`, `perfil`, `paywall` | Limpar template Expo | n |
| Backend futuro | Quarkus + Keycloak (não Supabase); ver AGENT_BRIDGE | Alinhado com agente backend | y |

**Open questions:** none — todas logadas como assumptions acima (aguardam confirmação do usuário).

---

## User Stories

### P1: NativeWind configurado ⭐ MVP

**User Story**: As a developer, I want NativeWind/Tailwind working so that screens use `className` consistently.

**Why P1**: Base de UI de todas as telas.

**Acceptance Criteria**:

1. WHEN o projeto inicia o Metro bundler THEN o app SHALL carregar estilos NativeWind sem erro de config (`tailwind.config.js`, `metro.config.js`, `babel.config.js`, `global.css`).
2. WHEN um componente usa `className` Tailwind válido THEN o estilo SHALL ser aplicado na UI.

**Independent Test**: Abrir Home e ver padding/cores aplicados via `className`.

---

### P1: Navegação por Tabs ⭐ MVP

**User Story**: As a tenista, I want three tabs (Home, Minhas Raquetes, Perfil) so that I can navigate the core app.

**Why P1**: Shell de navegação do produto.

**Acceptance Criteria**:

1. WHEN o app abre THEN system SHALL mostrar tabs: "Home", "Minhas Raquetes", "Perfil".
2. WHEN o usuário toca em cada tab THEN system SHALL navegar para a tela correspondente (`index`, `raquetes`, `perfil`).
3. WHEN a rota `app/(tabs)/_layout.tsx` renderiza THEN system SHALL não expor mais a tab template "Tab Two".

**Independent Test**: Percorrer as 3 tabs e ver títulos corretos.

---

### P1: Home com desgaste e detalhes da corda ⭐ MVP

**User Story**: As a tenista, I want to see current string wear and string details so that I know when to restring.

**Why P1**: Proposta de valor central.

**Acceptance Criteria**:

1. WHEN a Home monta com a raquete seed THEN system SHALL exibir progresso visual circular com uso atual e máximo (ex.: 12h de 30h).
2. WHEN a Home monta THEN system SHALL exibir marca/modelo da corda, libras e data de encordoamento da corda atual.
3. WHEN o usuário toca no botão "+" destacado THEN system SHALL registrar um treino rápido (+1h no uso da corda ativa) e atualizar o progresso.
4. WHEN não houver raquete ativa THEN system SHALL exibir estado vazio orientando cadastrar uma raquete.

**Independent Test**: Abrir Home com seed → ver 12/30h e detalhes Luxilon/52 lbs; tocar "+" → uso vira 13h.

---

### P1: Lista Minhas Raquetes ⭐ MVP

**User Story**: As a tenista, I want to see my rackets and add another so that I can track multiple frames later.

**Why P1**: Segunda aba pedida no brief.

**Acceptance Criteria**:

1. WHEN a aba abre THEN system SHALL listar raquetes em memória (ao menos a seed Babolat Pure Drive).
2. WHEN o usuário toca em adicionar THEN system SHALL incluir uma nova raquete no estado em memória e refletir na lista.

**Independent Test**: Ver 1 raquete; adicionar; lista mostra 2.

---

### P1: Perfil com CTA Premium ⭐ MVP

**User Story**: As a tenista, I want profile options and a clear Premium CTA so that I can discover paid plans.

**Why P1**: Funil para paywall.

**Acceptance Criteria**:

1. WHEN a aba Perfil abre THEN system SHALL mostrar opções básicas do usuário (placeholders: conta, notificações ou similar).
2. WHEN o usuário toca em "Seja Premium" THEN system SHALL navegar para a rota modal `paywall`.

**Independent Test**: Perfil → Seja Premium → paywall abre.

---

### P1: Paywall modal ⭐ MVP

**User Story**: As a tenista, I want a polished paywall with plans so that I understand Premium value.

**Why P1**: Monetização visual do MVP.

**Acceptance Criteria**:

1. WHEN a paywall abre THEN system SHALL usar fundo escuro `slate-900` e listar benefícios: Múltiplas raquetes, Sincronização automática, Gráficos de tensão.
2. WHEN a paywall abre THEN system SHALL oferecer seletor Mensal R$9,90 vs Anual R$79,90 (com indicação de 7 dias grátis no anual).
3. WHEN o usuário toca o CTA de checkout THEN system SHALL simular início de checkout (feedback local) sem processar pagamento real.
4. WHEN a rota é registrada THEN `paywall` SHALL ser apresentada como modal no root stack.

**Independent Test**: Abrir paywall, alternar planos, tocar CTA, ver feedback simulado.

---

### P1: Hook `useRackets` com seed ⭐ MVP

**User Story**: As a developer, I want an in-memory typed hook so that screens share racket/string/training state before Supabase.

**Why P1**: Fonte única de dados mock.

**Acceptance Criteria**:

1. WHEN o hook inicializa THEN system SHALL expor ao menos uma raquete seed: Babolat Pure Drive, corda Luxilon Alu Power, 52 lbs, 12h usadas, max 30h.
2. WHEN `addTrainingHours` / equivalente é chamado THEN o uso da corda ativa SHALL aumentar e consumidores re-renderizam.
3. WHEN `addRacket` é chamado THEN a lista SHALL incluir a nova raquete.
4. Types SHALL cobrir Racket, StringSetup (ou equivalente) e horas de uso em TypeScript strict.

**Independent Test**: Unit/logic check ou UI Home/Raquetes refletindo o seed e mutações.

---

## Edge Cases

- WHEN `hoursUsed` atinge ou ultrapassa `maxHours` THEN system SHALL ainda exibir progresso (clamp visual em 100%) e manter o valor numérico legível.
- WHEN lista de raquetes está vazia THEN Home SHALL mostrar empty state (não crashar).
- WHEN usuário cancela o Alert do "+" THEN system SHALL não alterar horas.
- WHEN paywall fecha THEN system SHALL voltar à tela anterior sem alterar estado premium (ainda não existe flag premium real).

---

## Implicit-Requirement Dimensions Sweep

| Dimension | Resolution |
| --------- | ---------- |
| Input validation & bounds | Horas de treino rápido = +1h fixo; clamp visual 0–100% |
| Failure / partial-failure | N/A — sem I/O de rede neste feature |
| Idempotency / retry | N/A — mutações locais síncronas |
| Auth boundaries | N/A — sem auth |
| Concurrency / ordering | N/A — single-threaded UI state |
| Data lifecycle / expiry | Estado volatiliza no reload do app (assumido) |
| Observability | N/A — MVP visual |
| External-dependency failure | N/A — sem Supabase/IAP ainda |
| State-transition integrity | Uso só aumenta via treino; sem downgrade premium |

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| -------------- | ----- | ----- | ------ |
| MVP-01 | P1: NativeWind | Execute | Verified |
| MVP-02 | P1: Tabs | Execute | Verified |
| MVP-03 | P1: Home progresso + detalhes | Execute | Verified |
| MVP-04 | P1: Home botão + treino rápido | Execute | Verified |
| MVP-05 | P1: Minhas Raquetes lista + add | Execute | Verified |
| MVP-06 | P1: Perfil + Seja Premium | Execute | Verified |
| MVP-07 | P1: Paywall UI/planos/CTA | Execute | Verified |
| MVP-08 | P1: useRackets seed + mutações | Execute | Verified |

**Coverage:** 8 total, 8 mapped to tasks, 0 unmapped

---

## Success Criteria

- [ ] Cold start mostra Home com dados de tênis realistas (Babolat / Luxilon / 12h de 30h)
- [ ] Três tabs navegáveis com labels corretos
- [ ] Paywall modal dark com planos e CTA simulado
- [ ] TypeScript strict sem erros nos arquivos novos
- [ ] Estrutura pronta para `/services` (Supabase) sem retrabalho de pastas
