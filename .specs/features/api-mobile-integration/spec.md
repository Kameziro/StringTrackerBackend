# API Mobile Integration — Specification

## Problem Statement

O shell MVP do StringTracker ainda usa estado em memória; o Quarkus já expõe raquetes e sessões sob JWT Keycloak. Sem integração real, o app não persiste uso de corda nem aplica o limite freemium no servidor. É preciso autenticar no Expo, consumir o contrato v1 e eliminar o mock como fonte de verdade.

## Goals

- [ ] Usuário faz login Keycloak (OIDC + PKCE) no app e obtém access token
- [ ] Home / Minhas Raquetes / treino rápido consomem `GET|POST /api/rackets` e `POST /api/sessions` com Bearer
- [ ] Mock em memória removido; loading com spinner; erros por status HTTP
- [ ] DTOs I/O documentados em `dtos.md` e tipados em `mobile/services` alinhados ao BE
- [ ] Schema PostgreSQL via **Flyway** (sem Hibernate `database.generation=update`)
## Out of Scope

| Feature | Reason |
| ------- | ------ |
| Novos endpoints além do contrato v1 | Contrato congelado (AD-007); ver `dtos.md` |
| PUT/DELETE raquetes, GET sessions | Fora do api-v1 |
| Upgrade premium persistido no BE / IAP | Paywall continua CTA simulado |
| Cache offline / AsyncStorage / retry automático | Decisão 4A — spinner + erro |
| Formulários dedicados de raquete/treino | Mantém UX Alert/prompt do mvp-shell |
| `maxHours` no servidor | Client-only (default 30) |
| Deploy cloud Keycloak/API | Ambiente local (compose) |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --------------------- | -------------- | --------- | ---------- |
| Auth mobile | OIDC Authorization Code + PKCE, client `stringtracker-expo` | Escolha 1A + realm existente | y |
| Fonte de dados | Só API; remove store/seed em memória | Escolha 2A | y |
| Erros UI | Por status (401/403 literal/404/rede/400) | Escolha 3B | y |
| Loading | Spinner; sem cache do último sucesso | Escolha 4A | y |
| `isPremium` | Role Keycloak `premium` no access token | Sem GET /me no v1 | n — assumption |
| Paywall CTA | Continua simulado (não altera BE) | Fora de escopo upgrade | y (deferred) |
| Raquete ativa | Client-only; default = primeira da lista | Sem preferência no v1 | n — assumption |
| Logout | Perfil → limpa tokens → login | Necessário com auth real | n — assumption |
| Base URL | `EXPO_PUBLIC_API_URL` default `http://localhost:8080` | Contrato | y |
| Cálculo horas | `+= durationMinutes / 60.0` no servidor | Contrato / AD bridge | y |
| Schema DB | Quarkus Flyway; `hibernate-orm.database.generation=none` | Pedido explícito do usuário | y |
| Migração inicial | `V1__…` cria tabelas User / Racket / PlaySession alinhadas ao modelo JPA atual | Espelha domínio já implementado | n — assumption |
| Perfil `%test` | Flyway também no test (H2) **ou** `drop-and-create` só em test se scripts forem PG-only | Preferir scripts portáveis; detalhe no Design | n — assumption |

**Open questions:** none — ambiguidade restante logada como assumptions acima.

---

## User Stories

### P1: Flyway migrations no backend ⭐ MVP

**User Story**: Como desenvolvedor, quero o schema do PostgreSQL versionado com Flyway para evoluir o banco sem `hibernate.database.generation=update`.

**Why P1**: Pedido explícito; schema reproduzível entre ambientes antes de plugar o mobile em dados reais.

**Acceptance Criteria**:

1. WHEN o backend sobe contra PostgreSQL THEN Quarkus Flyway SHALL aplicar migrations versionadas em `src/main/resources/db/migration/` (ou path canônico Quarkus)
2. WHEN Hibernate ORM estiver configurado THEN `quarkus.hibernate-orm.database.generation` SHALL ser `none` (não `update` / `create`)
3. WHEN o schema atual do domínio (User, Racket, PlaySession + FKs/índices necessários ao freemium) ainda não existir THEN a migration inicial SHALL criá-lo de forma equivalente ao modelo JPA vigente
4. WHEN uma migration falhar THEN a aplicação SHALL falhar ao subir (não silenciar drift de schema)

**Independent Test**: DB limpo + `mvn quarkus:dev` (ou test de migrate) → tabelas criadas só via Flyway; property `generation=none` presente.

---

### P1: Login Keycloak no Expo ⭐ MVP

**User Story**: Como tenista, quero entrar com minha conta Keycloak para que o app use minha identidade nas APIs.

**Why P1**: Sem token, nenhum endpoint autenticado funciona.

**Acceptance Criteria**:

1. WHEN o app abre sem access token válido THEN system SHALL apresentar fluxo de login Keycloak (OIDC + PKCE, client `stringtracker-expo`)
2. WHEN o login Keycloak conclui com sucesso THEN system SHALL armazenar o access token de forma segura o bastante para reuso na sessão do app e SHALL permitir acesso às tabs principais
3. WHEN o access token está ausente ou a API responde `401` THEN system SHALL exigir reautenticação (não manter o usuário em telas autenticadas como se a sessão fosse válida)
4. WHEN o usuário escolhe logout no Perfil THEN system SHALL limpar tokens e voltar ao fluxo de login

**Independent Test**: Login com `free.player` / `free123` → entra nas tabs; logout → volta ao login.

---

### P1: Cliente HTTP tipado + DTOs ⭐ MVP

**User Story**: Como desenvolvedor, quero um cliente em `mobile/services` tipado pelos DTOs do contrato para chamar a API sem drift de shape.

**Why P1**: Camada única de integração; base para as telas.

**Acceptance Criteria**:

1. WHEN o cliente monta requests THEN SHALL usar base URL configurável (`EXPO_PUBLIC_API_URL`, default `http://localhost:8080`) e header `Authorization: Bearer <token>`
2. WHEN tipagens wire existirem THEN SHALL espelhar `dtos.md`: `RacketResponse`, `CreateRacketRequest`, `CreatePlaySessionRequest` / `CreateSessionRequest`, `PlaySessionResponse` / `SessionResponse`
3. WHEN `GET /api/rackets` for chamado com token válido THEN system SHALL retornar a lista tipada ou propagar erro HTTP
4. WHEN `POST /api/rackets` / `POST /api/sessions` forem chamados THEN bodies SHALL corresponder aos Inputs de `dtos.md` (sem enviar `totalHoursPlayed` na criação de raquete)

**Independent Test**: Com token válido e API up, `listRackets()` retorna array JSON parseável nos tipos; create/session enviam payloads do contrato.

---

### P1: Substituir mock — listar e criar raquetes ⭐ MVP

**User Story**: Como tenista, quero ver e cadastrar raquetes persistidas no backend para acompanhar cordas de verdade.

**Why P1**: Substitui o mock na tab Minhas Raquetes (e lista usada na Home).

**Acceptance Criteria**:

1. WHEN a tela Minhas Raquetes monta com sessão válida THEN system SHALL buscar `GET /api/rackets`, exibir spinner durante o fetch, e renderizar a lista (ou estado vazio se `[]`)
2. WHEN o usuário adiciona uma raquete THEN system SHALL chamar `POST /api/rackets` com `CreateRacketRequest` e, em sucesso `201`, atualizar a UI com o `RacketResponse` retornado
3. WHEN a API responde `403` no create THEN system SHALL exibir a mensagem literal do body (limite freemium) e NÃO adicionar raquete localmente
4. WHEN a API responde erro de rede THEN system SHALL exibir mensagem de falha de rede (sem reutilizar lista cacheada de sucesso anterior)
5. WHEN esta feature estiver completa THEN o seed/store em memória NÃO SHALL ser a fonte de verdade das raquetes

**Independent Test**: Login free → 0 ou 1 raquete da API; 2ª create → Alert com texto exato do 403.

---

### P1: Treino via `POST /api/sessions` ⭐ MVP

**User Story**: Como tenista, quero registrar treino rápido (+1h) na API para atualizar o desgaste da corda no servidor.

**Why P1**: Home “+” deixa de mutar só memória.

**Acceptance Criteria**:

1. WHEN o usuário confirma treino rápido (+1h) na Home com raquete ativa THEN system SHALL `POST /api/sessions` com `racketId`, `durationMinutes: 60`, `datePlayed` = data local de hoje (ISO-8601)
2. WHEN a API responde `201` THEN system SHALL atualizar o progresso da Home usando `racketTotalHoursPlayed` (não inventar incremento só no client)
3. WHEN a API responde `404` THEN system SHALL informar que a raquete não foi encontrada / não está disponível
4. WHEN a request está em voo THEN system SHALL mostrar estado de loading e não permitir double-submit óbvio do mesmo gesto

**Independent Test**: Raquete com 12h → + → API → UI mostra 13h (ou valor retornado em `racketTotalHoursPlayed`).

---

### P2: Premium na UI a partir do JWT

**User Story**: Como tenista premium, quero que o Perfil reflita meu status a partir do token.

**Why P2**: Paywall/perfil já existem; sem GET /me, role no JWT é o mínimo alinhado ao realm.

**Acceptance Criteria**:

1. WHEN o access token contém role de realm `premium` THEN system SHALL tratar `isPremium = true` na UI de Perfil
2. WHEN não contém essa role THEN system SHALL tratar `isPremium = false`
3. WHEN o usuário usa o CTA do paywall THEN system SHALL manter o checkout **simulado** (não chama API de upgrade nesta feature)

**Independent Test**: Login `premium.player` → Perfil premium; `free.player` → não premium; CTA paywall só Alert.

---

## Edge Cases

- WHEN `GET /api/rackets` retorna `[]` THEN Home SHALL mostrar empty state orientando cadastrar raquete
- WHEN `durationMinutes` inválido no servidor THEN `400` e UI de validação/erro (client envia 60 no happy path)
- WHEN token expira no meio do uso THEN próximo `401` SHALL forçar reauth
- WHEN free user com 1 raquete tenta a 2ª THEN `403` com mensagem literal; UI não adiciona item fantasma
- WHEN `POST /api/sessions` usa `racketId` de outro usuário / inexistente THEN `404` e mensagem específica
- WHEN API está offline THEN spinner termina em erro de rede; sem lista “fantasma” de sucesso anterior

---

## Implicit-Requirement Dimensions

| Dimension | Resolution |
| --------- | ---------- |
| Input validation & bounds | Bean Validation no BE; client envia shapes de `dtos.md`; `durationMinutes > 0` |
| Failure / partial-failure | Erro por status; create/session não atualizam UI em falha; sem rollback client além de não aplicar sucesso |
| Idempotency / retry | Sem retry automático; double-submit do + evitado com loading |
| Auth boundaries & rate limits | Bearer obrigatório; rate limit N/A neste escopo |
| Concurrency / ordering | Última response vence na UI; BE já serializa freemium/horas |
| Data lifecycle / expiry | Token expiry → 401 → reauth; sem TTL de cache; schema via Flyway versionado |
| Observability | N/A além de erros de superfície; logs BE existentes |
| External-dependency failure | Keycloak ou API down → erro de rede / login falha |
| State-transition integrity | N/A (sem máquina de estados além de authed/unauthed) |

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| -------------- | ----- | ----- | ------ |
| AMI-00 | P1: Flyway migrations + generation=none | Design | Pending |
| AMI-01 | P1: Login Keycloak | Design | Pending |
| AMI-02 | P1: Login — 401/reauth + logout | Design | Pending |
| AMI-03 | P1: Cliente HTTP + tipos DTOs | Design | Pending |
| AMI-04 | P1: GET rackets na UI + spinner | Design | Pending |
| AMI-05 | P1: POST racket + 403 literal | Design | Pending |
| AMI-06 | P1: Remover mock como SoT | Design | Pending |
| AMI-07 | P1: POST sessions + update Home | Design | Pending |
| AMI-08 | P1: 404/rede/loading no treino e listas | Design | Pending |
| AMI-09 | P2: isPremium via role JWT | Design | Pending |
| AMI-10 | P2: Paywall CTA simulado | Design | Pending |

**Coverage:** 11 total, 0 mapped to tasks, 11 unmapped ⚠️

---

## Success Criteria

- [ ] Login Keycloak no device/emulator e chamadas `/api/*` com Bearer funcionam contra compose local
- [ ] Free user não cria 2ª raquete; UI mostra mensagem 403 exata
- [ ] Treino +1h reflete `racketTotalHoursPlayed` da API na Home
- [ ] Nenhum seed/store em memória como fonte de verdade das raquetes
- [ ] `dtos.md` e tipos TS batem com DTOs Java / api-v1
- [ ] Schema sobe só via Flyway; Hibernate `database.generation=none`

## Related docs

- DTOs: `.specs/features/api-mobile-integration/dtos.md`
- Contrato: `.specs/contracts/api-v1.md`
- Contexto: `.specs/features/api-mobile-integration/context.md`
- Bridge: `.specs/AGENT_BRIDGE.md` (X-03)
