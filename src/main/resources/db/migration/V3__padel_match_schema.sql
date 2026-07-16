-- Padel Match: perfil de jogador + clubes + jogos abertos + push tokens

ALTER TABLE users ADD COLUMN category INTEGER;
ALTER TABLE users ADD COLUMN available_today BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN available_today_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE clubs (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(255) NOT NULL,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    registration_date  TIMESTAMP WITH TIME ZONE NOT NULL,
    update_date        TIMESTAMP WITH TIME ZONE NOT NULL,
    exclusion_date     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_clubs_name UNIQUE (name)
);

CREATE TABLE device_tokens (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    expo_push_token    VARCHAR(255) NOT NULL,
    platform           VARCHAR(32) NOT NULL,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    registration_date  TIMESTAMP WITH TIME ZONE NOT NULL,
    update_date        TIMESTAMP WITH TIME ZONE NOT NULL,
    exclusion_date     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_device_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_device_tokens_token UNIQUE (expo_push_token)
);

CREATE INDEX idx_device_tokens_user_id ON device_tokens (user_id);

CREATE TABLE availability_slots (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    day_of_week        INT NOT NULL,
    start_time         TIME NOT NULL,
    end_time           TIME NOT NULL,
    club_id            BIGINT,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    registration_date  TIMESTAMP WITH TIME ZONE NOT NULL,
    update_date        TIMESTAMP WITH TIME ZONE NOT NULL,
    exclusion_date     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_availability_slots_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_availability_slots_club FOREIGN KEY (club_id) REFERENCES clubs (id),
    CONSTRAINT ck_availability_day CHECK (day_of_week BETWEEN 1 AND 7)
);

CREATE INDEX idx_availability_slots_user_id ON availability_slots (user_id);

CREATE TABLE open_games (
    id                 BIGSERIAL PRIMARY KEY,
    organizer_id       BIGINT NOT NULL,
    club_id            BIGINT NOT NULL,
    starts_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    category           INT NOT NULL,
    capacity           INT NOT NULL DEFAULT 4,
    status             VARCHAR(32) NOT NULL,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    registration_date  TIMESTAMP WITH TIME ZONE NOT NULL,
    update_date        TIMESTAMP WITH TIME ZONE NOT NULL,
    exclusion_date     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_open_games_organizer FOREIGN KEY (organizer_id) REFERENCES users (id),
    CONSTRAINT fk_open_games_club FOREIGN KEY (club_id) REFERENCES clubs (id),
    CONSTRAINT ck_open_games_capacity CHECK (capacity >= 2 AND capacity <= 8),
    CONSTRAINT ck_open_games_category CHECK (category BETWEEN 1 AND 8)
);

CREATE INDEX idx_open_games_category_status ON open_games (category, status);
CREATE INDEX idx_open_games_organizer_id ON open_games (organizer_id);

CREATE TABLE game_interests (
    id                 BIGSERIAL PRIMARY KEY,
    game_id            BIGINT NOT NULL,
    user_id            BIGINT NOT NULL,
    status             VARCHAR(32) NOT NULL,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    registration_date  TIMESTAMP WITH TIME ZONE NOT NULL,
    update_date        TIMESTAMP WITH TIME ZONE NOT NULL,
    exclusion_date     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_game_interests_game FOREIGN KEY (game_id) REFERENCES open_games (id),
    CONSTRAINT fk_game_interests_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_game_interests_game_user UNIQUE (game_id, user_id)
);

CREATE INDEX idx_game_interests_game_id ON game_interests (game_id);

INSERT INTO clubs (name, active, registration_date, update_date) VALUES
    ('Maranhão', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Met Pad', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Smart Pad', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Inner Pad', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
