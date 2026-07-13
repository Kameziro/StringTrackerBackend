# STATE

## Decisions

### AD-001
- **Decision**: Stack do app: Expo SDK 57 + Expo Router (file-based) + NativeWind/Tailwind + TypeScript strict.
- **Reason**: Alinhado ao pedido do MVP e ao AGENTS.md (docs Expo v57).
- **Trade-off**: NativeWind exige config Metro/Babel/CSS adicional vs StyleSheet puro.
- **Scope**: Projeto inteiro
- **Date**: 2026-07-12
- **Status**: active

### AD-002
- **Decision**: Arquitetura de pastas: `/app` (rotas), `/components`, `/hooks`, `/services` (cliente HTTP da API Quarkus).
- **Reason**: Separação limpa e expansível sem over-engineering no MVP.
- **Trade-off**: `/services` começa stub/mock até o contrato API estabilizar.
- **Scope**: Projeto inteiro
- **Date**: 2026-07-12
- **Status**: superseded by AD-009

### AD-003
- **Decision**: Estado de raquetes/treinos em memória via `mobile/hooks/useRackets.ts` até plugar a API Quarkus.
- **Reason**: Desbloquear UI do MVP sem backend pronto.
- **Trade-off**: Dados resetam ao reiniciar o app.
- **Scope**: Features de raquetes, treinos, Home
- **Date**: 2026-07-12
- **Status**: active

### AD-004
- **Decision**: Backend canônico é Quarkus 3.x em `backend/` + PostgreSQL; auth via Keycloak (JWT). Não usar Supabase.
- **Reason**: Decisão do usuário / agente backend; alinhar FE e BE no mesmo monorepo.
- **Trade-off**: App precisa de cliente OIDC (Expo) numa feature futura; MVP shell permanece mock.
- **Scope**: Projeto inteiro; contrato em `.specs/contracts/api-v1.md`; ponte em `.specs/AGENT_BRIDGE.md`
- **Date**: 2026-07-12
- **Status**: active

### AD-005
- **Decision**: Coordenação multi-agente via arquivos em `.specs/` (AGENT_BRIDGE + contracts), não via chat direto.
- **Reason**: Cursor não oferece bridge de mensagens entre chats Composer.
- **Trade-off**: Usuário precisa pedir a cada agente para ler/responder o bridge.
- **Scope**: Processo do projeto
- **Date**: 2026-07-12
- **Status**: active

### AD-006
- **Decision**: Autenticação da API via Keycloak (Bearer JWT) usando `quarkus-oidc` com `application-type=service`; identidade = claim `sub`; JIT User local com `keycloakId`.
- **Reason**: Consenso AGENT_BRIDGE + pedido Keycloak; OIDC é o caminho oficial Quarkus.
- **Trade-off**: Requer Keycloak (ou Dev Services) local; `User` ganha `keycloakId`.
- **Scope**: `backend/` API `/api/*`
- **Date**: 2026-07-12
- **Status**: active

### AD-007
- **Decision**: Contrato HTTP canônico é `.specs/contracts/api-v1.md` (confirmed); backend implementa, frontend tipa em `mobile/services`.
- **Reason**: X-01 fechado via AGENT_BRIDGE; evita drift FE↔BE.
- **Trade-off**: Mudanças de shape exigem atualizar contrato + bridge antes do código.
- **Scope**: FE `mobile/services` + BE resources/DTOs
- **Date**: 2026-07-12
- **Status**: active

### AD-008
- **Decision**: Packages backend `br.com.stringtracker.{model,repository,resource,dto}` (+ `service` para JIT) sob pasta `backend/`.
- **Reason**: Pedido explícito de camadas no bootstrap Quarkus.
- **Trade-off**: Service layer mínima além do pedido original de 4 packages.
- **Scope**: backend Java
- **Date**: 2026-07-12
- **Status**: active

### AD-009
- **Decision**: Monorepo com apps separados na raiz: `mobile/` (Expo) e `backend/` (Quarkus); `.specs/` compartilhado.
- **Reason**: Pedido do usuário para separar backend do mobile na pasta raiz.
- **Trade-off**: Scripts e cwd mudam (`cd mobile` / `cd backend`); paths históricos em validation.md ficam relativos ao layout antigo.
- **Scope**: Projeto inteiro
- **Date**: 2026-07-12
- **Status**: active

## Handoff

- **Feature**: monorepo layout (mobile/ + backend/)
- **Phase / Task**: Move Expo → `mobile/` concluído
- **Completed**: git mv mobile; README/AGENTS/AD-009
- **In-progress**: none
- **Next step (FE)**: `cd mobile && npx expo start`; plugar API quando BE up
- **Next step (BE)**: continuar em `backend/` (outro agente); WIP backend não incluído neste commit de layout
- **Blockers**: none
- **Uncommitted files**: WIP `backend/**` do outro agente; tooling `.agents`/`.cursor`
- **Branch**: master
