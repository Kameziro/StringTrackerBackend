-- Drop legacy StringTracker (rackets/strings) and unused premium flag

DROP TABLE IF EXISTS play_sessions;
DROP TABLE IF EXISTS rackets;

ALTER TABLE users DROP COLUMN is_premium;
