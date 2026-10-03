# Padel Match API (Quarkus)

Backend REST do app de matching de jogos de padel. O cliente mobile (Expo) **não fala com o Keycloak**: login, cadastro, refresh e logout passam por um BFF em `/api/auth/*`.

Contrato resumido: [`.specs/contracts/api-v1.md`](.specs/contracts/api-v1.md).  
Decisões de produto: [`.specs/STATE.md`](.specs/STATE.md).

## O que a API faz

- Cadastro e login de jogadores (BFF Keycloak + perfil local)
- Perfil com categoria (1–8), cidade, disponibilidade e avatar
- Grupos de jogadores (entrar/sair, papéis, avatar e banner)
- Jogos abertos por categoria e/ou grupo, com interesse e confirmação
- Lista de jogadores disponíveis hoje na mesma cidade e categoria
- Push via Expo Push API quando um jogo é publicado
- Proxy público de mídia (React Native Image não envia `Authorization`)

## Stack

| Camada | Tecnologia |
|--------|------------|
| Runtime | Java 21, Quarkus 3.20 |
| HTTP | RESTEasy Reactive + Jackson |
| Persistência | Hibernate ORM Panache, PostgreSQL 16, Flyway |
| Auth | Keycloak 26 (`quarkus-oidc`) + BFF password grant |
| Storage | MinIO (S3-compatível) para avatars/banners |
| Push | Expo Push API (`https://exp.host`) |
| Testes | JUnit 5, REST Assured, H2 em memória, `quarkus-test-security-jwt` |

## Arquitetura (visão rápida)

```
App Expo ──► POST /api/auth/login|register|refresh
                │
                ▼
           Quarkus BFF ──► Keycloak (realm stringtracker)
                │
                ▼
           JWT Bearer ──► /api/me, /api/games, /api/groups, …
                │
                ├── PostgreSQL (perfil, grupos, jogos)
                ├── MinIO (imagens) ── proxy GET /api/media/{key}
                └── Expo Push API
```

Pacote base: `br.com.stringtracker`.

| Pacote | Responsabilidade |
|--------|------------------|
| `resource` | JAX-RS (`/api/...`) + mappers de erro |
| `service` | Regras de negócio |
| `repository` | Panache |
| `model` | Entidades JPA (`BaseEntity` com soft-delete) |
| `dto` | Request/response |
| `client` | REST clients (Keycloak Admin/Token, Expo) |

Todas as entidades herdam `BaseEntity` (`active`, `registration_date`, `update_date`, `exclusion_date`). Queries filtram `active = true`.

## Pré-requisitos

- JDK 21
- Maven 3.9+
- Docker + Docker Compose
- Cópia local de `.env` (veja abaixo)

## Subir o ambiente

```bash
cp .env.example .env
# Ajuste senhas. O secret do client stringtracker-api no .env
# deve bater com keycloak/realm-stringtracker.json (dev: stringtracker-api-secret).

# Postgres + MinIO (bucket padelmatch-avatars)
docker compose up -d postgres minio minio-init

# Stack completa (inclui Keycloak na 8180, realm importado):
docker compose --profile full up -d
```

| Serviço | URL |
|---------|-----|
| API | `http://localhost:8080` |
| Keycloak | `http://localhost:8180` (admin: `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD`) |
| MinIO S3 API | `http://localhost:9000` |
| MinIO Console | `http://localhost:9001` |

Credenciais MinIO: `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` no `.env`.

Quarkus carrega `.env` automaticamente no `quarkus:dev`. O Compose usa o mesmo arquivo para Postgres, MinIO e Keycloak.

### Rodar a API

```bash
mvn quarkus:dev
```

Base URL: `http://localhost:8080`

Dev UI (quando `quarkus:dev` está no ar): `http://localhost:8080/q/dev`.

## Variáveis de ambiente

Definidas em [`.env.example`](.env.example):

| Variável | Uso |
|----------|-----|
| `QUARKUS_HTTP_PORT` | Porta HTTP (padrão `8080`) |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Banco da API e do container |
| `POSTGRES_JDBC_URL` | JDBC (`jdbc:postgresql://localhost:5432/stringtracker`) |
| `OIDC_AUTH_SERVER_URL` | Realm OIDC (`http://localhost:8180/realms/stringtracker`) |
| `OIDC_CLIENT_ID` / `OIDC_CLIENT_SECRET` | Client confidencial `stringtracker-api` |
| `OIDC_TOKEN_ISSUER` | Issuer esperado no JWT |
| `AUTH_KEYCLOAK_CLIENT_ID` | Client público do BFF (`stringtracker-expo`, password grant) |
| `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` | Admin do container + cadastro via Admin API |
| `KEYCLOAK_ADMIN_BASE_URL` | Base do Admin API (`http://localhost:8180`) |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | Credenciais MinIO |
| `MINIO_BUCKET_AVATARS` | Bucket (`padelmatch-avatars`) |
| `MINIO_ENDPOINT` | Endpoint S3 visto pela API (`http://localhost:9000`) |
| `MINIO_REGION` | Região S3 (`us-east-1`) |

`MINIO_PUBLIC_BASE_URL` não é mais usado: imagens saem pelo proxy `GET /api/media/...` no mesmo host da API (necessário no iPhone físico, onde o app não consegue autenticar o Image).

## Autenticação

Rotas públicas (`@PermitAll`): `/api/auth/*`, `/api/geo/*`, `/api/media/*`.  
Demais rotas exigem `Authorization: Bearer <accessToken>`.

### Usuários de desenvolvimento (realm importado)

| Username | Senha | Roles |
|----------|-------|-------|
| `free.player` | `free123` | `user` |
| `premium.player` | `premium123` | `user`, `premium` |

### Login

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"free.player","password":"free123"}'
```

Resposta:

```json
{
  "accessToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 300,
  "refreshToken": "...",
  "refreshExpiresIn": 1800
}
```

### Cadastro, refresh e logout

```bash
# Disponibilidade de e-mail
curl -s "http://localhost:8080/api/auth/email-available?email=novo@exemplo.com"

# Cadastro (cria usuário no Keycloak + perfil local e já devolve tokens)
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"novo@exemplo.com","password":"secret1","name":"Ana Silva","category":3,"cityId":1}'

# Refresh
curl -s -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refresh>"}'

# Logout (invalida o refresh no Keycloak)
curl -s -X POST http://localhost:8080/api/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refresh>"}'
```

`register` exige `email`, senha (6–128), `name` (2–120), `category` (1–8) e `cityId`.

## API

Prefixo: `/api`. JSON UTF-8. CORS liberado em dev.

### Geo (público)

Usado no onboarding antes do login.

| Método | Rota | Notas |
|--------|------|-------|
| `GET` | `/api/geo/states` | Estados (UF) ordenados |
| `GET` | `/api/geo/cities?stateId=` | Obrigatório `stateId` |

### Auth (público)

| Método | Rota | Status típico |
|--------|------|----------------|
| `POST` | `/api/auth/login` | `200` / `401` / `502` |
| `POST` | `/api/auth/register` | `201` / `409` e-mail em uso / `502` IdP |
| `POST` | `/api/auth/refresh` | `200` / `401` sessão expirada |
| `POST` | `/api/auth/logout` | `204` |
| `GET` | `/api/auth/email-available?email=` | `{ "available": true }` |

### Perfil (`/api/me`)

| Método | Rota | Corpo / notas |
|--------|------|----------------|
| `GET` | `/api/me/profile` | `{ id, name, email, category, cityId, cityName, stateId, stateUf, availableToday, availableTodayAt, avatarUrl? }` |
| `PUT` | `/api/me/profile` | `{ name, category (1–8), cityId, availableToday }` |
| `POST` | `/api/me/profile/avatar` | `multipart` campo `file` (JPEG/PNG/WebP, máx. ~5 MB) |
| `GET` | `/api/me/availability` | Slots semanais |
| `PUT` | `/api/me/availability` | `{ slots: [{ dayOfWeek (1–7), startTime, endTime, clubId? }] }` |
| `POST` | `/api/me/device-token` | `{ expoPushToken, platform }` → `204` |

### Clubes e jogadores

| Método | Rota | Notas |
|--------|------|-------|
| `GET` | `/api/clubs` | Clubes ativos (seed: Maranhão, Met Pad, Smart Pad, Inner Pad) |
| `GET` | `/api/players?availableToday=true` | Mesma categoria e cidade do usuário autenticado. Sem `availableToday=true` devolve lista vazia. Exige perfil com categoria e cidade. |

### Grupos

Papéis: `ADMIN` (criador), `MODERATOR`, `MEMBER`.

| Método | Rota | Notas |
|--------|------|-------|
| `GET` | `/api/groups` | Todos, com `joined` e `memberCount` |
| `GET` | `/api/groups?mine=true` | Só os que o usuário participa |
| `GET` | `/api/groups/{id}` | Detalhe + membros (`myRole`, `members[].role`) |
| `POST` | `/api/groups` | `{ name }` — cria e entra como admin |
| `POST` | `/api/groups/{id}/join` | |
| `POST` | `/api/groups/{id}/leave` | Não pode sair se for o único admin |
| `POST` | `/api/groups/{id}/avatar` | Multipart `file` (admin/mod) |
| `POST` | `/api/groups/{id}/banner` | Multipart `file` (admin/mod) |
| `PUT` | `/api/groups/{id}/members/{userId}/role` | `{ role: ADMIN\|MODERATOR\|MEMBER }` (só admin) |

### Jogos abertos

Status: `OPEN`, `FULL`, `CONFIRMED`, `CANCELLED`.  
Interesse: `INTERESTED`, `DECLINED`, `CONFIRMED`.

| Método | Rota | Notas |
|--------|------|-------|
| `GET` | `/api/games` | Abertos visíveis: mesma categoria sem grupo **ou** jogos dos grupos do usuário |
| `GET` | `/api/games?mine=true` | Jogos que o usuário organizou ou nos quais demonstrou interesse |
| `POST` | `/api/games` | Ver payload abaixo |
| `GET` | `/api/games/{id}` | |
| `POST` | `/api/games/{id}/interest` | Mesma categoria/cidade; se o jogo for de grupo, precisa ser membro |
| `POST` | `/api/games/{id}/decline` | |
| `POST` | `/api/games/{id}/confirm` | Só o organizador |

`POST /api/games`:

```json
{
  "place": "Met Pad",
  "startsAt": "2026-08-20T22:00:00Z",
  "endsAt": "2026-08-20T23:30:00Z",
  "category": 3,
  "capacity": 4,
  "groupId": null
}
```

- `place` — nome livre (clube/quadra). O clube é criado sob demanda se ainda não existir.
- `capacity` — opcional, 2–8 (padrão 4).
- `groupId` omitido/null → notifica jogadores da mesma categoria (push Expo).
- `groupId` preenchido → notifica só membros; o organizador precisa ser membro do grupo.

### Mídia (público)

`GET /api/media/{objectKey}` faz proxy do MinIO. Aceita:

- `users/{id}/avatar.{jpg\|jpeg\|png\|webp}`
- `groups/{id}/avatar.*` e `groups/{id}/banner.*`
- legado: `avatars/users/...`

Cache: `Cache-Control: public, max-age=3600`.

O `avatarUrl` / `bannerUrl` devolvido pela API aponta para esse proxy (mesmo host), por exemplo:

`/api/media/users/1/avatar.webp?v=…`

## Erros HTTP comuns

| Status | Quando |
|--------|--------|
| `400` | Validação / regra de negócio (`BadRequestException`) |
| `401` | Credenciais inválidas ou JWT ausente |
| `403` | Sem permissão (ex.: confirmar jogo sem ser organizador) |
| `404` | Recurso ou arquivo de mídia inexistente |
| `409` | E-mail já cadastrado |
| `502` | Keycloak indisponível ou perfil IdP incompleto |

## Banco (Flyway)

Migrações em `src/main/resources/db/migration/`. Rodam no start (`quarkus.flyway.migrate-at-start=true`). Hibernate **não** gera schema (`database.generation=none`).

| Versão | Conteúdo |
|--------|----------|
| V1 | Schema legado (users / rackets) |
| V2 | Colunas de auditoria em `BaseEntity` |
| V3 | Padel Match: categoria, clubes, jogos, disponibilidade, device tokens |
| V4 | Remove rackets/sessões (produto só matching) |
| V5–V10 | Grupos, estados/cidades + seed, avatar de usuário, avatar/banner de grupo, papéis |

Seed de clubes (V3) e estados/cidades (V7) entra na primeira subida.

## Testes

```bash
mvn test
```

Perfil `%test`: H2 em memória (`MODE=PostgreSQL`), Flyway limpa e reaplica, OIDC desligado, JWT de teste via `quarkus-test-security-jwt`. Integração nativa (`-Pnative`) está no `pom.xml` mas ITs vêm desligados por padrão (`skipITs`).

## Deploy (VPS)

`docker-compose.prod.yml` sobe tudo em containers e publica só a API, em `127.0.0.1:8080`; Postgres, MinIO e Keycloak ficam na rede interna. O Nginx do host termina o HTTPS de `padel.kameziro.com.br` e encaminha para a API (`deploy/nginx/`). O realm de produção (`realm-stringtracker.prod.json`) não tem usuários de teste e lê o secret da API do `.env`.

Na VPS (Ubuntu com Docker, Nginx e certbot), com o DNS apontando para ela:

```bash
git clone https://github.com/Kameziro/StringTrackerBackend.git /opt/padelmatch && cd /opt/padelmatch
cp .env.prod.example .env   # preencha os secrets: openssl rand -hex 24
docker compose -f docker-compose.prod.yml up -d --build
certbot certonly --nginx -d padel.kameziro.com.br
ln -s /opt/padelmatch/deploy/nginx/padel.kameziro.com.br.conf /etc/nginx/sites-enabled/padel
nginx -t && systemctl reload nginx
```

Atualizar: `git pull && docker compose -f docker-compose.prod.yml up -d --build`.

O console do Keycloak não é exposto. Para usá-lo, publique `127.0.0.1:8180:8180` no serviço `keycloak` e abra um túnel: `ssh -L 8180:localhost:8180 root@<vps>`.

## Estrutura do repositório

```
.
├── docker-compose.yml          # postgres, minio, minio-init, keycloak (profile full)
├── docker-compose.prod.yml     # produção: + api em 127.0.0.1:8080, sem portas internas expostas
├── deploy/nginx/               # site do Nginx do host (HTTPS + proxy para a API)
├── Dockerfile                  # imagem da API (JVM)
├── keycloak/realm-stringtracker.json
├── keycloak/realm-stringtracker.prod.json
├── .env.example
├── .env.prod.example
├── pom.xml                     # artifact stringtracker-api
├── .specs/
│   ├── contracts/api-v1.md
│   └── STATE.md
└── src/main/java/br/com/stringtracker/
    ├── resource/
    ├── service/
    ├── repository/
    ├── model/
    ├── dto/
    └── client/
```

## Troubleshooting

- **401 no login com Keycloak no ar** — confira `OIDC_*` e `AUTH_KEYCLOAK_CLIENT_ID=stringtracker-expo`. O client da API precisa do secret igual ao do realm.
- **502 no login/cadastro** — Keycloak ainda não subiu ou realm não importou. Espere o health do Postgres e o log `Keycloak ... started`.
- **Imagem não abre no celular** — use a URL de `/api/media/...` (mesmo host da API). Não aponte o app direto para a porta 9000 do MinIO.
- **MinIO no Docker, API no host** — `MINIO_ENDPOINT=http://localhost:9000`. Se a API também rodar em container na mesma rede, use `http://minio:9000`.
- **`Complete seu perfil com a categoria`** — `GET /api/players` e `POST /api/games` exigem categoria e cidade no perfil.
