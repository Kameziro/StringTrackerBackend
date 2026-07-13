# Backend Quarkus Bootstrap — Specification

## Problem Statement

O app Expo StringTracker precisa de uma API Java/Quarkus para persistir usuários, raquetes e sessões de jogo, aplicando a regra de negócio do limite de 1 raquete para usuários gratuitos e acumulando horas jogadas na raquete. Hoje o repositório só tem o frontend; o backend ainda não existe.

## Goals

- [ ] Projeto Maven Quarkus 3.x em `backend/` com package `br.com.stringtracker` e camadas model / repository / resource / dto
- [ ] Entidades JPA User, Racket, PlaySession com relacionamentos corretos
- [ ] Endpoints REST: listar/criar raquetes (com limite free) e registrar sessão (atualizando `totalHoursPlayed`)
- [ ] PostgreSQL local + CORS liberado para o frontend Expo

## Out of Scope

| Feature | Reason |
| ------- | ------ |
| UI/fluxo de login no app Expo | Frontend consome Keycloak depois; bootstrap é API |
| CRUD admin de usuários na API | Cadastro/login ficam no Keycloak |
| PUT/DELETE de raquetes e listagem de sessões | Não solicitados neste bootstrap |
| Deploy cloud de Keycloak/API | Ambiente local; compose opcional conforme decisão |
| Sincronização avançada Keycloak Admin API | Fora do bootstrap salvo decisão explícita |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --------------------- | -------------- | --------- | ---------- |
| Identidade do “usuário autenticado” sem JWT | Header `X-User-Id` (Long); se ausente → 401 | JWT futuro; precisa de identidade para filtrar raquetes e aplicar limite free | n — **gray area** |
| Seed de usuário de desenvolvimento | Não seed automático; caller informa `X-User-Id` de um User existente | Evita dados mágicos; endpoint de User fora de escopo → pode precisar de seed SQL mínimo | n — **gray area** |
| Cálculo de `totalHoursPlayed` | `+= durationMinutes / 60.0` | Campo é horas (double); sessão chega em minutos | n — **gray area** |
| Formato do erro 403 (limite free) | Body texto plano com a mensagem exata pedida | Spec do usuário cita a mensagem literal | y (do pedido) |
| Quarkus / Java | Quarkus 3.15+ LTS ou latest 3.x estável; Java 21 | Padrão atual Quarkus 3.x | y (best practice) |
| Grupo Maven | `br.com.stringtracker` / artifact `stringtracker-api` | Alinha ao package | y |
| Validação de inputs | Bean Validation básica (`@NotNull`, `@Positive`, etc.) nos DTOs | Robustez mínima sem expandir escopo | y |

**Open questions:** ver seção Discuss abaixo — auth stub, seed e cálculo de horas precisam confirmação.

---

## User Stories

### P1: Bootstrap do projeto Quarkus ⭐ MVP

**User Story**: Como desenvolvedor, quero um projeto Maven Quarkus em `backend/` com as extensões RESTEasy Reactive Jackson, Hibernate ORM Panache, JDBC PostgreSQL e SmallRye JWT, para ter base pronta da API.

**Why P1**: Sem o projeto, nada mais funciona.

**Acceptance Criteria**:

1. WHEN o diretório `backend/` for criado THEN o sistema SHALL conter `pom.xml` Maven com as quatro extensões solicitadas
2. WHEN a aplicação subir com PostgreSQL em `jdbc:postgresql://localhost:5432/stringtracker` THEN Hibernate SHALL usar `database.generation=update`
3. WHEN o frontend Expo chamar a API local THEN CORS SHALL permitir origens (configuração CORS habilitada no `application.properties`)
4. WHEN o código for organizado THEN packages SHALL ser `br.com.stringtracker.{model,repository,resource,dto}`

**Independent Test**: `mvn -f backend/pom.xml quarkus:dev` inicia sem erro de compile (DB pode falhar se Postgres offline — compile/package deve passar).

---

### P1: Domínio JPA e repositórios ⭐ MVP

**User Story**: Como desenvolvedor, quero entidades User, Racket e PlaySession com repositórios Panache para persistir o domínio de cordas/raquetes.

**Why P1**: Base de dados do produto.

**Acceptance Criteria**:

1. WHEN User for mapeado THEN SHALL ter `id`, `name`, `email`, `isPremium` (boolean)
2. WHEN Racket for mapeado THEN SHALL ter `id`, relacionamento ManyToOne com User, `brand`, `model`, `tensionLbs` (double), `stringModel`, `dateStrung`, `totalHoursPlayed` (double)
3. WHEN PlaySession for mapeado THEN SHALL ter `id`, relacionamento ManyToOne com Racket, `durationMinutes` (int), `datePlayed` (LocalDate)
4. WHEN repositórios existirem THEN `UserRepository`, `RacketRepository` e `PlaySessionRepository` SHALL implementar `PanacheRepository<Entity>`

**Independent Test**: Schema gerado pelo Hibernate reflete as colunas/FKs; repositórios injetáveis via CDI.

---

### P1: API de raquetes com limite free ⭐ MVP

**User Story**: Como usuário do app, quero listar e cadastrar raquetes, sendo bloqueado no 2º cadastro se não for Premium.

**Why P1**: Regra de negócio central do freemium.

**Acceptance Criteria**:

1. WHEN `GET /api/rackets` com identidade válida THEN system SHALL retornar a lista de raquetes daquele usuário
2. WHEN `POST /api/rackets` com usuário Premium THEN system SHALL criar a raquete e retornar 201
3. WHEN `POST /api/rackets` com usuário free (`isPremium == false`) que já possui ≥1 raquete THEN system SHALL retornar **403 Forbidden** com mensagem exatamente: `Limite de raquetes atingido para usuários gratuitos. Faça o upgrade para o Premium!`
4. WHEN `POST /api/rackets` com usuário free sem raquetes THEN system SHALL criar a primeira raquete e retornar 201

**Independent Test**: Com User free sem raquete → POST ok; segundo POST → 403 com mensagem; User premium → múltiplos POSTs ok.

---

### P1: Registro de sessão e acumulação de horas ⭐ MVP

**User Story**: Como tenista, quero registrar uma sessão de jogo para que as horas da raquete aumentem automaticamente.

**Why P1**: Núcleo do rastreador de vida útil das cordas.

**Acceptance Criteria**:

1. WHEN `POST /api/sessions` com payload válido THEN system SHALL persistir `PlaySession` ligada à raquete
2. WHEN a sessão for salva THEN system SHALL incrementar `Racket.totalHoursPlayed` de acordo com a duração (ver decisão de cálculo)
3. WHEN a raquete informada não existir THEN system SHALL retornar 404

**Independent Test**: Criar raquete com `totalHoursPlayed=0`, POST sessão 90 min → raquete passa a refletir horas acumuladas (ex.: 1.5 se minutos/60).

---

## Edge Cases

- WHEN `X-User-Id` (ou mecanismo escolhido) apontar para User inexistente THEN system SHALL retornar 401 ou 404 (conforme decisão de auth stub)
- WHEN `POST /api/sessions` com `durationMinutes <= 0` THEN system SHALL rejeitar com 400
- WHEN listagem de raquetes para usuário sem raquetes THEN system SHALL retornar lista vazia `[]` (200)
- WHEN falha de persistência no meio de sessão+update de horas THEN system SHALL rolar em uma única transação (`@Transactional`)

---

## Implicit-Requirement Dimensions

| Dimension | Resolution |
| --------- | ---------- |
| Input validation & bounds | Bean Validation nos DTOs; duração > 0 |
| Failure / partial-failure | `@Transactional` em create racket e create session |
| Idempotency / retry | N/A — bootstrap sem idempotency keys |
| Auth boundaries | Stub até JWT real (gray area) |
| Concurrency | N/A for this scope — sem locking otimista neste bootstrap |
| Data lifecycle | N/A — sem soft-delete/TTL |
| Observability | N/A — logs default Quarkus |
| External-dependency failure | Postgres down → falha de startup/request (aceitável no bootstrap) |
| State-transition integrity | Limite free: free + count≥1 → Forbidden |

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| -------------- | ----- | ----- | ------ |
| BE-01 | P1: Bootstrap Quarkus | Design | Pending |
| BE-02 | P1: application.properties (DB + CORS + generation) | Design | Pending |
| BE-03 | P1: Entity User | Design | Pending |
| BE-04 | P1: Entity Racket | Design | Pending |
| BE-05 | P1: Entity PlaySession | Design | Pending |
| BE-06 | P1: Panache repositories | Design | Pending |
| BE-07 | P1: DTOs request/response conforme api-v1.md | Design | Pending |
| BE-08 | P1: GET /api/rackets | Design | Pending |
| BE-09 | P1: POST /api/rackets + limite free 403 | Design | Pending |
| BE-10 | P1: POST /api/sessions + update totalHoursPlayed + racketTotalHoursPlayed | Design | Pending |
| BE-11 | P1: SmallRye JWT + OIDC Keycloak | Design | Pending |
| BE-12 | P1: Contrato api-v1 confirmed + AGENT_BRIDGE | Design | Verified |

**Coverage:** 11 total, 0 mapped to tasks, 11 unmapped

---

## Success Criteria

- [ ] `backend/` compila com Maven e estrutura de packages correta
- [ ] Free user bloqueado no 2º racket com mensagem 403 exata
- [ ] Sessão incrementa `totalHoursPlayed` atomicamente
- [ ] CORS e JDBC PostgreSQL configurados conforme pedido
`)