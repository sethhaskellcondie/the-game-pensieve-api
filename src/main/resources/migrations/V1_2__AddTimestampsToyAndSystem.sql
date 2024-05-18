ALTER TABLE systems
    ADD created_at timestamp with time zone NOT NULL default now(),
    ADD updated_at timestamp with time zone NOT NULL default now(),
    ADD deleted_at timestamp with time zone NULL;

ALTER TABLE toys
    ADD created_at timestamp with time zone NOT NULL default now(),
    ADD updated_at timestamp with time zone NOT NULL default now(),
    ADD deleted_at timestamp with time zone NULL;

-- Undo
-- ALTER TABLE systems
--     DROP COLUMN created_at,
--     DROP COLUMN updated_at,
--     DROP COLUMN deleted_at;
--
-- ALTER TABLE toys
--     DROP COLUMN created_at,
--     DROP COLUMN updated_at,
--     DROP COLUMN deleted_at;
--
-- DELETE FROM flyway_schema_history WHERE version = '1.2';