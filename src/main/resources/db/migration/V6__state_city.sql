-- Estados e cidades (IBGE) + vínculo no perfil do jogador

CREATE TABLE state (
    id         BIGSERIAL PRIMARY KEY,
    ibge_code  BIGINT UNIQUE,
    uf         VARCHAR(2)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE city (
    id         BIGSERIAL PRIMARY KEY,
    ibge_code  BIGINT UNIQUE,
    name       VARCHAR(100) NOT NULL,
    state_id   BIGINT NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_city_state FOREIGN KEY (state_id) REFERENCES state (id)
);

CREATE INDEX idx_city_state_id ON city (state_id);
CREATE INDEX idx_city_name ON city (name);

ALTER TABLE users ADD COLUMN city_id BIGINT;
ALTER TABLE users
    ADD CONSTRAINT fk_users_city FOREIGN KEY (city_id) REFERENCES city (id);

CREATE INDEX idx_users_city_id ON users (city_id);
