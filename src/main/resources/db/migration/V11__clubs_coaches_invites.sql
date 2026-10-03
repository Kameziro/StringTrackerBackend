-- Clube como cliente: perfil, conta de recebimento, administradores, professores e convites.
-- clubs.active já existe (BaseEntity, V3), por isso não é recriada aqui.

ALTER TABLE clubs ADD COLUMN address VARCHAR(255);
ALTER TABLE clubs ADD COLUMN whatsapp VARCHAR(20);
ALTER TABLE clubs ADD COLUMN logo_url VARCHAR(512);
ALTER TABLE clubs ADD COLUMN payment_status VARCHAR(16) NOT NULL DEFAULT 'NOT_CONNECTED';
ALTER TABLE clubs ADD COLUMN mp_user_id VARCHAR(32);
ALTER TABLE clubs ADD COLUMN mp_access_token_enc TEXT;
ALTER TABLE clubs ADD COLUMN mp_refresh_token_enc TEXT;
ALTER TABLE clubs ADD COLUMN mp_token_expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE clubs
    ADD CONSTRAINT ck_clubs_payment_status CHECK (payment_status IN ('NOT_CONNECTED', 'CONNECTED'));

ALTER TABLE users ADD COLUMN platform_admin BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE club_photos (
    id                 BIGSERIAL PRIMARY KEY,
    club_id            BIGINT NOT NULL,
    url                VARCHAR(512) NOT NULL,
    position           INT NOT NULL,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    registration_date  TIMESTAMP WITH TIME ZONE NOT NULL,
    update_date        TIMESTAMP WITH TIME ZONE NOT NULL,
    exclusion_date     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_club_photos_club FOREIGN KEY (club_id) REFERENCES clubs (id)
);

CREATE INDEX idx_club_photos_club_id ON club_photos (club_id);

CREATE TABLE club_admins (
    id                 BIGSERIAL PRIMARY KEY,
    club_id            BIGINT NOT NULL,
    user_id            BIGINT NOT NULL,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    registration_date  TIMESTAMP WITH TIME ZONE NOT NULL,
    update_date        TIMESTAMP WITH TIME ZONE NOT NULL,
    exclusion_date     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_club_admins_club FOREIGN KEY (club_id) REFERENCES clubs (id),
    CONSTRAINT fk_club_admins_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_club_admins_club_user UNIQUE (club_id, user_id)
);

CREATE INDEX idx_club_admins_user_id ON club_admins (user_id);

CREATE TABLE coaches (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    bio                TEXT,
    offers_singles     BOOLEAN NOT NULL DEFAULT TRUE,
    offers_doubles     BOOLEAN NOT NULL DEFAULT TRUE,
    offers_group       BOOLEAN NOT NULL DEFAULT FALSE,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    registration_date  TIMESTAMP WITH TIME ZONE NOT NULL,
    update_date        TIMESTAMP WITH TIME ZONE NOT NULL,
    exclusion_date     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_coaches_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_coaches_user UNIQUE (user_id)
);

CREATE TABLE club_coaches (
    id                    BIGSERIAL PRIMARY KEY,
    club_id               BIGINT NOT NULL,
    coach_id              BIGINT NOT NULL,
    price_singles_cents   BIGINT,
    price_doubles_cents   BIGINT,
    price_group_cents     BIGINT,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    registration_date     TIMESTAMP WITH TIME ZONE NOT NULL,
    update_date           TIMESTAMP WITH TIME ZONE NOT NULL,
    exclusion_date        TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_club_coaches_club FOREIGN KEY (club_id) REFERENCES clubs (id),
    CONSTRAINT fk_club_coaches_coach FOREIGN KEY (coach_id) REFERENCES coaches (id),
    CONSTRAINT uk_club_coaches_club_coach UNIQUE (club_id, coach_id)
);

CREATE INDEX idx_club_coaches_coach_id ON club_coaches (coach_id);

CREATE TABLE invites (
    id                 BIGSERIAL PRIMARY KEY,
    token_hash         VARCHAR(64) NOT NULL,
    email              VARCHAR(255) NOT NULL,
    kind               VARCHAR(16) NOT NULL,
    club_id            BIGINT NOT NULL,
    invited_by         BIGINT,
    expires_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at        TIMESTAMP WITH TIME ZONE,
    accepted_by        BIGINT,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    registration_date  TIMESTAMP WITH TIME ZONE NOT NULL,
    update_date        TIMESTAMP WITH TIME ZONE NOT NULL,
    exclusion_date     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_invites_club FOREIGN KEY (club_id) REFERENCES clubs (id),
    CONSTRAINT fk_invites_invited_by FOREIGN KEY (invited_by) REFERENCES users (id),
    CONSTRAINT fk_invites_accepted_by FOREIGN KEY (accepted_by) REFERENCES users (id),
    CONSTRAINT uk_invites_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_invites_kind CHECK (kind IN ('CLUB_ADMIN', 'COACH'))
);

CREATE INDEX idx_invites_club_id ON invites (club_id);
