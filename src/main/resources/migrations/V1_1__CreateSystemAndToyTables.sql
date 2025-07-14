-- CREATE DATABASE "pensieve-db"
--     WITH
--     OWNER = postgres
--     ENCODING = 'UTF8'
--     CONNECTION LIMIT = -1
--     IS_TEMPLATE = False;

CREATE TABLE IF NOT EXISTS systems ( --table names are plural and snake case
    id SERIAL PRIMARY KEY, --field names are snake case, ids are numeric
    name VARCHAR NOT NULL,
    generation INT NOT NULL DEFAULT 0,
    handheld BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS toys (
    id SERIAL PRIMARY KEY, --SERIAL is the Postgres way of saying INTEGER NOT NULL AUTO INCREMENT
    name VARCHAR NOT NULL,
    set VARCHAR NULL
);

-- Undo
-- I had this in its own migration file, but I now know that is only a feature in the paid version of flyway (Flyway Teams)
--
-- DROP TABLE toys;
--
-- DROP TABLE systems;
--
-- DELETE FROM flyway_schema_history WHERE version = '1.1';
