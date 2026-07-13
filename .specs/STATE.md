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
- **Status**: superseded by AD-011

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

### AD-010
- **Decision**: Schema do PostgreSQL via Quarkus Flyway; `quarkus.hibernate-orm.database.generation=none` (não usar `update`/`create` em runtime).
- **Reason**: Pedido explícito do usuário na feature api-mobile-integration; schema versionado e reproduzível.
- **Trade-off**: Toda mudança de modelo exige migration SQL; onboarding DB limpo depende de `V1__…` correta.
- **Scope**: `backend/` (substitui AC de ddl `update` do backend-bootstrap em ambientes não-test)
- **Date**: 2026-07-12
- **Status**: active

### AD-011
- **Decision**: Fonte de verdade de raquetes/treinos no mobile é a API Quarkus (`mobile/services` + hooks); store/seed em memória removido.
- **Reason**: Spec api-mobile-integration (2A) + X-03 AGENT_BRIDGE.
- **Trade-off**: App requer API+Keycloak up para dados; sem offline.
- **Scope**: `mobile/hooks`, `mobile/services`, telas Home/Raquetes
- **Date**: 2026-07-12
- **Status**: active

### AD-012
- **Decision**: Mobile NÃO fala com Keycloak. Auth via BFF: `POST /api/auth/login` no Quarkus (service troca username/password por token no IdP); app só usa `EXPO_PUBLIC_API_URL` + Bearer.
- **Reason**: Pedido do usuário — Keycloak/OIDC no Expo é complexidade desnecessária; backend concentra identidade.
- **Trade-off**: Backend precisa de password-grant (ou equivalente) ao IdP; client público Expo deixa de ser usado pelo app.
- **Scope**: `backend` auth resource/service; `mobile/services/auth`, login UI; supersede AMI auth 1A (AuthSession)
- **Date**: 2026-07-12
- **Status**: active

### AD-013
- **Decision**: Todas as entidades JPA estendem `BaseEntity` (`id`, `active`, `registration_date`, `update_date`, `exclusion_date`) com soft-delete via `markExcluded()`.
- **Reason**: Pedido explícito do usuário para model base compartilhada.
- **Trade-off**: Listagens/contagens filtram `active = true`; exclusão física fica fora do padrão.
- **Scope**: `backend/.../model`, repositórios, Flyway `V2__…`
- **Date**: 2026-07-12
- **Status**: active

## Handoff

- **Feature**: api-mobile-integration (auth BFF amend AD-012)
- **Phase / Task**: Implementando POST /api/auth/login + login form mobile
- **Completed**: AMI vertical slice anterior (Verifier PASS)
- **In-progress**: remover AuthSession / KEYCLOAK_* do mobile
- **Next step**: BE AuthResource + FE form
- **Blockers**: none
- **Uncommitted files**: WIP backend unrelated
- **Branch**: master
