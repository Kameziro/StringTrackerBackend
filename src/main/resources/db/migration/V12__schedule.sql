-- Agenda dos professores: blocos semanais, horários concretos e bloqueio de dias.
-- btree_gist é necessária para combinar igualdade (coach_id) e sobreposição de intervalo no EXCLUDE.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE schedule_blocks (
    id                 BIGSERIAL PRIMARY KEY,
    club_coach_id      BIGINT NOT NULL,
    kind               VARCHAR(8) NOT NULL,
    day_of_week        SMALLINT NOT NULL,
    start_time         TIME NOT NULL,
    end_time           TIME NOT NULL,
    duration_minutes   SMALLINT NOT NULL,
    capacity           SMALLINT NOT NULL DEFAULT 1,
    title              VARCHAR(80),
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    registration_date  TIMESTAMP WITH TIME ZONE NOT NULL,
    update_date        TIMESTAMP WITH TIME ZONE NOT NULL,
    exclusion_date     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_schedule_blocks_club_coach FOREIGN KEY (club_coach_id) REFERENCES club_coaches (id),
    CONSTRAINT ck_schedule_blocks_kind CHECK (kind IN ('PRIVATE', 'GROUP')),
    CONSTRAINT ck_schedule_blocks_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_schedule_blocks_duration CHECK (duration_minutes IN (60, 90)),
    CONSTRAINT ck_schedule_blocks_capacity CHECK (capacity BETWEEN 1 AND 8)
);

CREATE INDEX idx_schedule_blocks_club_coach_id ON schedule_blocks (club_coach_id);

CREATE TABLE lesson_slots (
    id                 BIGSERIAL PRIMARY KEY,
    schedule_block_id  BIGINT,
    club_coach_id      BIGINT NOT NULL,
    coach_id           BIGINT NOT NULL,
    starts_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    kind               VARCHAR(8) NOT NULL,
    capacity           SMALLINT NOT NULL,
    status             VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    registration_date  TIMESTAMP WITH TIME ZONE NOT NULL,
    update_date        TIMESTAMP WITH TIME ZONE NOT NULL,
    exclusion_date     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_lesson_slots_block FOREIGN KEY (schedule_block_id) REFERENCES schedule_blocks (id),
    CONSTRAINT fk_lesson_slots_club_coach FOREIGN KEY (club_coach_id) REFERENCES club_coaches (id),
    CONSTRAINT fk_lesson_slots_coach FOREIGN KEY (coach_id) REFERENCES coaches (id),
    CONSTRAINT ck_lesson_slots_kind CHECK (kind IN ('PRIVATE', 'GROUP')),
    CONSTRAINT ck_lesson_slots_status CHECK (status IN ('OPEN', 'BLOCKED', 'REMOVED')),
    -- AGND-03: o mesmo professor nunca tem dois horários sobrepostos, em qualquer clube
    CONSTRAINT ex_lesson_slots_coach_overlap EXCLUDE USING gist (
        coach_id WITH =,
        tstzrange(starts_at, ends_at) WITH &&
    ) WHERE (status <> 'REMOVED')
);

CREATE INDEX idx_lesson_slots_club_coach_starts_at ON lesson_slots (club_coach_id, starts_at);
CREATE INDEX idx_lesson_slots_block_id ON lesson_slots (schedule_block_id);

CREATE TABLE day_blocks (
    id                 BIGSERIAL PRIMARY KEY,
    coach_id           BIGINT NOT NULL,
    club_id            BIGINT,
    day                DATE NOT NULL,
    created_by         BIGINT,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    registration_date  TIMESTAMP WITH TIME ZONE NOT NULL,
    update_date        TIMESTAMP WITH TIME ZONE NOT NULL,
    exclusion_date     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_day_blocks_coach FOREIGN KEY (coach_id) REFERENCES coaches (id),
    CONSTRAINT fk_day_blocks_club FOREIGN KEY (club_id) REFERENCES clubs (id),
    CONSTRAINT fk_day_blocks_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE INDEX idx_day_blocks_coach_day ON day_blocks (coach_id, day);
