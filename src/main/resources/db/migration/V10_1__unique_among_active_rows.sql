-- Linhas excluídas (active = false) não bloqueiam a recriação: a unicidade vale só entre as ativas.
-- users fica de fora de propósito: conta desativada continua dona do keycloak_id e do e-mail.

ALTER TABLE clubs DROP CONSTRAINT uk_clubs_name;
CREATE UNIQUE INDEX uk_clubs_name ON clubs (name) WHERE active;

ALTER TABLE player_groups DROP CONSTRAINT uk_player_groups_name;
CREATE UNIQUE INDEX uk_player_groups_name ON player_groups (name) WHERE active;

ALTER TABLE group_members DROP CONSTRAINT uk_group_members_group_user;
CREATE UNIQUE INDEX uk_group_members_group_user ON group_members (group_id, user_id) WHERE active;

ALTER TABLE game_interests DROP CONSTRAINT uk_game_interests_game_user;
CREATE UNIQUE INDEX uk_game_interests_game_user ON game_interests (game_id, user_id) WHERE active;

ALTER TABLE device_tokens DROP CONSTRAINT uk_device_tokens_token;
CREATE UNIQUE INDEX uk_device_tokens_token ON device_tokens (expo_push_token) WHERE active;
