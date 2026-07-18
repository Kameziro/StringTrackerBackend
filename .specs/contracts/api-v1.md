# API v1 — Padel Match

Auth: `POST /api/auth/login` (BFF Keycloak) → Bearer JWT em todas as rotas abaixo.

## Perfil

- `GET /api/me/profile` → `{ id, name, email, category, availableToday, avatarUrl? }`
- `PUT /api/me/profile` `{ name, category (1–8), availableToday }`
- `POST /api/me/profile/avatar` multipart `file` (image/jpeg|png|webp, máx. ~5MB) → `avatarUrl` = `/api/media/avatars/users/{id}/avatar.ext?v=…`
- `GET /api/media/avatars/{objectKey}` — público, proxy do MinIO (mesmo host da API)
- `GET|PUT /api/me/availability` slots semanais
- `POST /api/me/device-token` `{ expoPushToken, platform }`

## Clubes e jogadores

- `GET /api/clubs`
- `GET /api/players?availableToday=true`

## Grupos

- `GET /api/groups` — todos (com `joined` + `memberCount`)
- `GET /api/groups?mine=true` — só os que participo
- `GET /api/groups/{id}` — detalhe + lista de membros
- `POST /api/groups` `{ name }` — cria e entra
- `POST /api/groups/{id}/join`
- `POST /api/groups/{id}/leave`
- `POST /api/groups/{id}/avatar` multipart `file` → detalhe com `avatarUrl` (admin/mod)
- `POST /api/groups/{id}/banner` multipart `file` → detalhe com `bannerUrl` (admin/mod)
- `PUT /api/groups/{id}/members/{userId}/role` `{ role: ADMIN|MODERATOR|MEMBER }` (admin)
- `GET /api/media/{objectKey}` — público (`users/...`, `groups/...`)
- Detalhe do grupo inclui `myRole` e `members[].role`

## Jogos

- `GET /api/games` — abertos visíveis (categoria sem grupo + jogos dos meus grupos)
- `GET /api/games?mine=true`
- `POST /api/games` `{ place, startsAt, endsAt, category, capacity?, groupId? }`
  - `place` — lugar livre (clube, quadra, etc.); criado sob demanda se ainda não existir
  - `groupId` omitido/null → notifica toda a categoria
  - `groupId` preenchido → notifica só membros do grupo (organizador precisa ser membro)
- `GET /api/games/{id}`
- `POST /api/games/{id}/interest`
- `POST /api/games/{id}/decline`
- `POST /api/games/{id}/confirm` (organizador)
