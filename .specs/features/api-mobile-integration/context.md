# API Mobile Integration — Context

**Gathered:** 2026-07-12  
**Spec:** `.specs/features/api-mobile-integration/spec.md`  
**Status:** Ready for design (pending user confirm)

---

## Feature Boundary

Plugar o app Expo às APIs Quarkus do contrato v1 (raquetes + sessões), com login Keycloak real no mobile, sem store em memória, erros por status HTTP, e loading com spinner sem cache offline. DTOs documentados em `dtos.md`.

---

## Implementation Decisions

### 1. Auth no Expo

- **AD-012**: mobile NÃO fala com Keycloak
- Tela de login com username/password → `POST /api/auth/login` no Quarkus
- Backend (`AuthService`) faz password-grant no IdP e devolve `accessToken`
- Access token em SecureStore; Bearer nas demais rotas `/api/*`

### 2. Substituição do mock

- Remover estado em memória (`racketStore` / seed local) como fonte de verdade
- Listagem, criação de raquete e treino passam exclusivamente pela API
- UI das tabs existentes continua; só a camada de dados muda

### 3. Erros na UI

- Mensagens por status HTTP:
  - `401` → reauth / sessão inválida
  - `403` → texto literal do backend (limite freemium)
  - `404` → raquete inexistente / não pertence ao usuário
  - rede / timeout / unreachable → mensagem de falha de rede
  - `400` → validação (mensagem genérica de dados inválidos se body não for texto útil)

### 4. Loading / falha de rede

- Spinner (ou equivalente) enquanto a request está em voo
- Em falha: erro visível; **sem** cache/persistência do último sucesso
- Sem retry automático nesta feature

### 5. Migrations (pedido pós-discuss)

- Schema PostgreSQL versionado com **Flyway** (`quarkus-flyway`)
- Desligar Hibernate schema auto: `quarkus.hibernate-orm.database.generation=none`
- Migration inicial cobre User / Racket / PlaySession alinhados ao JPA atual

### Agent's Discretion

- Biblioteca OIDC Expo (ex.: `expo-auth-session` + SecureStore) — escolher no Design
- Layout mínimo da tela de login (branding leve alinhado ao shell existente)
- Como mapear `RacketResponse.id` (number) ↔ `id` string no domínio FE, se ainda útil
- Scripts Flyway portáveis vs. PG-only + strategy do perfil `%test`

### Declined / Undiscussed Gray Areas → Assumptions

| Gray area | Chosen default | Rationale |
| --------- | -------------- | --------- |
| Fonte de `isPremium` sem GET /me | Claim/role Keycloak `premium` no access token; paywall CTA continua **simulado** (não grava premium no BE) | Contrato v1 não tem endpoint de perfil/upgrade; role já existe no realm |
| Raquete “ativa” na Home | Seleção client-only: primeira da lista se nenhuma escolhida | Sem endpoint de preferência no v1 |
| Logout | Botão no Perfil limpa tokens e volta ao login | Necessário com auth real; UX mínima |
| Base URL | `http://localhost:8080` + env `EXPO_PUBLIC_API_URL` | Contrato + AD-007; Android emulator tratado no Design |

---

## Specific References

- Contrato canônico: `.specs/contracts/api-v1.md`
- Bridge X-03: trocar mock `useRackets` por `/services/api`
- DTOs Java existentes em `backend/.../dto/`
- Users de teste Keycloak: `free.player` / `premium.player` (ver realm JSON)

---

## Deferred Ideas

- PUT/DELETE raquetes, GET sessions
- Sync `isPremium` Postgres ↔ role Keycloak / endpoint de upgrade
- AsyncStorage / cache offline
- Formulários dedicados de raquete/treino (mantém Alert/prompt do shell)
- `maxHours` persistido no servidor
