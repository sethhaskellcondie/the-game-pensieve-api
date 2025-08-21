CREATE TABLE IF NOT EXISTS metadata (
    id SERIAL PRIMARY KEY,
    key VARCHAR NOT NULL UNIQUE,
    value JSONB,
    created_at timestamp with time zone NOT NULL default now(),
    updated_at timestamp with time zone NOT NULL default now(),
    deleted_at timestamp with time zone NULL
);

-- Undo
-- DROP TABLE metadata;
-- DELETE FROM flyway_schema_history WHERE version = '1.7';