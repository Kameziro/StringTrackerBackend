# API Contract v1 — StringTracker

**Status:** confirmed (Backend 2026-07-12 — alinhado com AGENT_BRIDGE)  
**Base URL (dev):** `http://localhost:8080`  
**Auth:** `Authorization: Bearer <Keycloak access_token>`  
**Content-Type:** `application/json`

---

## Resources

### GET `/api/rackets`

Lista raquetes do usuário autenticado (via JWT → User local).

**Response 200**

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

**Errors:** `401` sem/invalid token

---

### POST `/api/rackets`

Cria raquete para o usuário autenticado.

**Request**

```json
{
  "brand": "Babolat",
  "model": "Pure Drive",
  "stringModel": "Luxilon Alu Power",
  "tensionLbs": 52.0,
  "dateStrung": "2026-06-01"
}
```

Notas:
- `totalHoursPlayed` inicia em `0` no servidor (omitido no request).
- Free user com ≥1 raquete → **403** texto plano:
  `Limite de raquetes atingido para usuários gratuitos. Faça o upgrade para o Premium!`

**Response 201:** mesmo shape de um item do GET.

**Errors:** `400` validação, `401`, `403` limite free

---

### POST `/api/sessions`

Registra treino/jogo e acumula horas na raquete.

**Request**

```json
{
  "racketId": 1,
  "durationMinutes": 60,
  "datePlayed": "2026-07-12"
}
```

**Server rule:** `racket.totalHoursPlayed += durationMinutes / 60.0` (transação única).

**Response 201**

```json
{
  "id": 10,
  "racketId": 1,
  "durationMinutes": 60,
  "datePlayed": "2026-07-12",
  "racketTotalHoursPlayed": 13.0
}
```

**Errors:** `400` se `durationMinutes <= 0`, `401`, `404` raquete inexistente / de outro user

---

## Domínio (alinhamento FE ↔ BE)

| Conceito FE (mock) | Conceito BE (JPA) |
| ------------------ | ----------------- |
| Racket + string details | `Racket` |
| hoursUsed | `totalHoursPlayed` |
| maxHours (vida útil) | **client-only** por enquanto (default 30) |
| addTraining(+1h) | `POST /api/sessions` com `durationMinutes: 60` |
| User premium flag | `User.isPremium` (local) + futuro role Keycloak |

---

## Fora deste contrato (v1)

- PUT/DELETE raquetes
- GET sessions
- Campo `maxHours` persistido
- Webhooks de pagamento / IAP

---

## Auth BFF (amend 2026-07-12 — AD-012)

Mobile **não** chama Keycloak. Login via API:

### POST `/api/auth/login`

**Auth:** público (sem Bearer)

**Request**

```json
{
  "username": "free.player",
  "password": "free123"
}
```

**Response 200**

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 300
}
```

**Errors:** `400` validação, `401` credenciais inválidas

Demais `/api/*` continuam com `Authorization: Bearer <accessToken>` (JWT emitido pelo IdP; Quarkus valida via OIDC).
