-- CREATE DATABASE "local-game-pensive"
--     WITH
--     OWNER = postgres
--     ENCODING = 'UTF8'
--     CONNECTION LIMIT = -1
--     IS_TEMPLATE = False;

CREATE TABLE IF NOT EXISTS Systems ( --table names are plural and camel case
    id SERIAL NOT NULL PRIMARY KEY, --field names are snake case, ids are numeric
    name VARCHAR NOT NULL,
    handheld BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS Toys (
    id SERIAL NOT NULL PRIMARY KEY, --SERIAL is the Postgres way of saying AUTO INCREMENT
    name VARCHAR NOT NULL,
    set VARCHAR NULL
);
