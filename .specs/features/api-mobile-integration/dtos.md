# API Mobile Integration — DTOs (Inputs / Outputs)

**Contrato canônico:** `.specs/contracts/api-v1.md`  
**Base URL (dev):** `http://localhost:8080`  
**Auth (endpoints autenticados):** header `Authorization: Bearer <access_token>` (obtido via `POST /api/auth/login`)  
**Content-Type:** `application/json`

Este arquivo isola shape de request/response por endpoint. Mudanças de campo exigem atualizar contrato + este arquivo + tipos em `mobile/services` + DTOs Java.

---

## 0. `POST /api/auth/login`

Login BFF (AD-012). Mobile envia credenciais; backend obtém JWT no IdP.

### Input — `LoginRequest`

| Campo | Tipo JSON | Obrigatório | Validação |
| ----- | --------- | ----------- | --------- |
| `username` | string | sim | não blank |
| `password` | string | sim | não blank |

### Output — `200 OK` — `LoginResponse`

| Campo | Tipo JSON | Notas |
| ----- | --------- | ----- |
| `accessToken` | string | JWT Bearer para `/api/*` |
| `tokenType` | string | sempre `Bearer` |
| `expiresIn` | number | segundos (eco do IdP quando disponível) |

### Errors

| Status | Quando |
| ------ | ------ |
| `400` | validação |
| `401` | credenciais inválidas / IdP rejeitou |

---

## Headers comuns (endpoints autenticados)

| Header | Obrigatório | Valor |
| ------ | ----------- | ----- |
| `Authorization` | sim | `Bearer <access_token>` |
| `Content-Type` | em POST com body | `application/json` |
| `Accept` | recomendado | `application/json` |

---

## 1. `GET /api/rackets`

Lista raquetes do usuário autenticado (JWT → User local JIT).

### Input

| Campo | Local | Tipo | Obrigatório | Notas |
| ----- | ----- | ---- | ----------- | ----- |
| — | body | — | — | Sem body |
| — | query | — | — | Sem query params |
| `Authorization` | header | string | sim | Bearer JWT |

### Output — `200 OK` — `RacketResponse[]`

| Campo | Tipo JSON | Tipo Java / TS | Notas |
| ----- | --------- | -------------- | ----- |
| `id` | number | `Long` / `number` | PK servidor |
| `brand` | string | `String` | |
| `model` | string | `String` | |
| `stringModel` | string | `String` | Corda atual |
| `tensionLbs` | number | `double` | Libras |
| `dateStrung` | string | `LocalDate` | ISO-8601 date (`YYYY-MM-DD`) |
| `totalHoursPlayed` | number | `double` | Horas acumuladas |

**Exemplo:**

```json
[
  {
    "id": 1,
    "brand": "Babolat",
    "model": "Pure Drive",
    "stringModel": "Luxilon Alu Power",
    "tensionLbs": 52.0,
    "dateStrung": "2026-06-01",
    "totalHoursPlayed": 12.0
  }
]
```

Lista vazia `[]` é válida (usuário sem raquetes).

### Errors

| Status | Body | Quando |
| ------ | ---- | ------ |
| `401` | (Quarkus default / vazio) | Sem token ou token inválido |

---

## 2. `POST /api/rackets`

Cria raquete para o usuário autenticado.

### Input — `CreateRacketRequest`

| Campo | Tipo JSON | Tipo Java / TS | Obrigatório | Validação |
| ----- | --------- | -------------- | ----------- | --------- |
| `brand` | string | `String` | sim | não blank |
| `model` | string | `String` | sim | não blank |
| `stringModel` | string | `String` | sim | não blank |
| `tensionLbs` | number | `Double` | sim | `> 0` |
| `dateStrung` | string | `LocalDate` | sim | ISO-8601 date |

**Não enviar:** `id`, `totalHoursPlayed` (servidor define; horas iniciam em `0`).

**Exemplo:**

```json
{
  "brand": "Babolat",
  "model": "Pure Drive",
  "stringModel": "Luxilon Alu Power",
  "tensionLbs": 52.0,
  "dateStrung": "2026-06-01"
}
```

### Output — `201 Created` — `RacketResponse`

Mesmo shape de um item do GET (ver tabela acima).  
`totalHoursPlayed` SHALL ser `0` na criação.

### Errors

| Status | Body | Quando |
| ------ | ---- | ------ |
| `400` | Bean Validation (Quarkus) | Campos inválidos / ausentes |
| `401` | — | Sem/invalid token |
| `403` | texto plano (não JSON) | Free user já com ≥1 raquete |

**Mensagem 403 literal (obrigatória):**

```text
Limite de raquetes atingido para usuários gratuitos. Faça o upgrade para o Premium!
```

---

## 3. `POST /api/sessions`

Registra treino/jogo e acumula horas na raquete (`totalHoursPlayed += durationMinutes / 60.0`) em transação única.

### Input — `CreatePlaySessionRequest`

| Campo | Tipo JSON | Tipo Java / TS | Obrigatório | Validação |
| ----- | --------- | -------------- | ----------- | --------- |
| `racketId` | number | `Long` | sim | ID de raquete do usuário |
| `durationMinutes` | number | `Integer` | sim | `> 0` |
| `datePlayed` | string | `LocalDate` | sim | ISO-8601 date |

**Exemplo (treino rápido +1h):**

```json
{
  "racketId": 1,
  "durationMinutes": 60,
  "datePlayed": "2026-07-12"
}
```

### Output — `201 Created` — `PlaySessionResponse`

| Campo | Tipo JSON | Tipo Java / TS | Notas |
| ----- | --------- | -------------- | ----- |
| `id` | number | `Long` | PK da sessão |
| `racketId` | number | `Long` | Raquete usada |
| `durationMinutes` | number | `int` | Eco do request |
| `datePlayed` | string | `LocalDate` | ISO-8601 date |
| `racketTotalHoursPlayed` | number | `double` | Total **após** o incremento |

**Exemplo:**

```json
{
  "id": 10,
  "racketId": 1,
  "durationMinutes": 60,
  "datePlayed": "2026-07-12",
  "racketTotalHoursPlayed": 13.0
}
```

### Errors

| Status | Body | Quando |
| ------ | ---- | ------ |
| `400` | Bean Validation | `durationMinutes <= 0` ou campos inválidos |
| `401` | — | Sem/invalid token |
| `404` | — | Raquete inexistente ou de outro usuário |

---

## Mapeamento FE ↔ BE

| DTO / tipo wire | Java (`br.com.stringtracker.dto`) | TypeScript (`mobile/services`) |
| --------------- | --------------------------------- | ------------------------------ |
| Create racket | `CreateRacketRequest` | `CreateRacketRequest` / `NewRacketInput` |
| Racket | `RacketResponse` | `RacketResponse` |
| Create session | `CreatePlaySessionRequest` | `CreateSessionRequest` |
| Session | `PlaySessionResponse` | `SessionResponse` |

| Conceito FE (UI) | Campo wire |
| ---------------- | ---------- |
| hoursUsed / progresso | `totalHoursPlayed` |
| maxHours (vida útil) | **client-only** (default 30) — não vem da API |
| Treino rápido +1h | `POST /api/sessions` com `durationMinutes: 60` |
| Premium (UI) | role Keycloak `premium` no JWT (não é campo destes DTOs) |

---

## Fora do v1 (não documentar como DTO desta feature)

- PUT/PATCH/DELETE `/api/rackets/{id}`
- GET `/api/sessions`
- GET `/api/me` / upgrade premium
- Signup/register (continua no IdP / feature futura)
- Refresh token endpoint (MVP: re-login)
- OIDC/AuthSession no app Expo (removido — AD-012)
