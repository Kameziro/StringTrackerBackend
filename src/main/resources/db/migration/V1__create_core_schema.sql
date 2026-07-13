-- Core schema aligned with JPA entities User, Racket, PlaySession
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    keycloak_id     VARCHAR(64)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    is_premium      BOOLEAN      NOT NULL,
    CONSTRAINT uk_users_keycloak_id UNIQUE (keycloak_id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE rackets (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT         NOT NULL,
    brand               VARCHAR(255)   NOT NULL,
    model               VARCHAR(255)   NOT NULL,
    tension_lbs         DOUBLE PRECISION NOT NULL,
    string_model        VARCHAR(255)   NOT NULL,
    date_strung         DATE           NOT NULL,
    total_hours_played  DOUBLE PRECISION NOT NULL,
    CONSTRAINT fk_rackets_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_rackets_user_id ON rackets (user_id);

CREATE TABLE play_sessions (
    id                BIGSERIAL PRIMARY KEY,
    racket_id         BIGINT  NOT NULL,
    duration_minutes  INT     NOT NULL,
    date_played       DATE    NOT NULL,
    CONSTRAINT fk_play_sessions_racket FOREIGN KEY (racket_id) REFERENCES rackets (id)
);

CREATE INDEX idx_play_sessions_racket_id ON play_sessions (racket_id);
