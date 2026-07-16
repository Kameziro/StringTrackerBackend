-- Player groups: narrower audience than full category

CREATE TABLE player_groups (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(120) NOT NULL,
    created_by_id      BIGINT NOT NULL,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    registration_date  TIMESTAMP WITH TIME ZONE NOT NULL,
    update_date        TIMESTAMP WITH TIME ZONE NOT NULL,
    exclusion_date     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_player_groups_name UNIQUE (name),
    CONSTRAINT fk_player_groups_creator FOREIGN KEY (created_by_id) REFERENCES users (id)
);

CREATE TABLE group_members (
    id                 BIGSERIAL PRIMARY KEY,
    group_id           BIGINT NOT NULL,
    user_id            BIGINT NOT NULL,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    registration_date  TIMESTAMP WITH TIME ZONE NOT NULL,
    update_date        TIMESTAMP WITH TIME ZONE NOT NULL,
    exclusion_date     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_group_members_group FOREIGN KEY (group_id) REFERENCES player_groups (id),
    CONSTRAINT fk_group_members_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_group_members_group_user UNIQUE (group_id, user_id)
);

CREATE INDEX idx_group_members_user_id ON group_members (user_id);
CREATE INDEX idx_group_members_group_id ON group_members (group_id);

ALTER TABLE open_games ADD COLUMN group_id BIGINT;
ALTER TABLE open_games
    ADD CONSTRAINT fk_open_games_group FOREIGN KEY (group_id) REFERENCES player_groups (id);
