-- Drop the last remnants of the homegrown HS256 auth stack (replaced by Keycloak in V1_19). The system is
-- unreleased, so the columns are removed outright rather than kept for backwards compatibility:
--   password_hash — passwords live in Keycloak; no code reads or writes the column.
--   enabled       — account disabling is done at the IdP (Keycloak's own enabled flag); never consulted here.
ALTER TABLE users DROP COLUMN password_hash;
ALTER TABLE users DROP COLUMN enabled;

-- Undo
-- ALTER TABLE users ADD COLUMN password_hash VARCHAR;
-- ALTER TABLE users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
--
-- DELETE FROM flyway_schema_history WHERE version = '1.20';
