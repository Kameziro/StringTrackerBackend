-- Roles: ADMIN | MODERATOR | MEMBER
ALTER TABLE group_members
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'MEMBER';

ALTER TABLE group_members
    ADD CONSTRAINT ck_group_members_role
        CHECK (role IN ('ADMIN', 'MODERATOR', 'MEMBER'));

-- Criador do grupo vira ADMIN
UPDATE group_members gm
SET role = 'ADMIN'
FROM player_groups pg
WHERE gm.group_id = pg.id
  AND gm.user_id = pg.created_by_id
  AND gm.active = TRUE;
