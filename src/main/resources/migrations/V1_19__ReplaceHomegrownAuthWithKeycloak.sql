-- Auth replacement: Keycloak (RS256) becomes the single authorization server for web + MCP, and the homegrown
-- HS256 login/refresh stack is removed. Identity now arrives as an OAuth access token; the users row is the
-- authorization/profile record, linked to the Keycloak account by the immutable subject claim.

-- keycloak_sub is the immutable Keycloak `sub` claim, the stable link between a token and its users row. It is
-- nullable so seeded rows (created by email) start unlinked and get their sub stamped on the owner's first
-- login (claim-on-first-login in OwnerResolver); UNIQUE so a sub maps to exactly one owner.
ALTER TABLE users ADD COLUMN keycloak_sub VARCHAR UNIQUE; -- NULL = not yet linked to a Keycloak account

-- password_hash is no longer written: passwords live in Keycloak now. Existing rows keep their (now-unused)
-- hash; JIT-provisioned and future rows leave it NULL, so the NOT NULL constraint is dropped.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- Refresh tokens were part of the homegrown rotation flow; Keycloak owns refresh now, so the table goes.
DROP TABLE IF EXISTS refresh_tokens;

-- Undo
-- CREATE TABLE refresh_tokens (
--     id SERIAL PRIMARY KEY,
--     user_id INT NOT NULL REFERENCES users(id),
--     token_hash VARCHAR NOT NULL,
--     expires_at timestamp with time zone NOT NULL,
--     revoked BOOLEAN NOT NULL DEFAULT FALSE,
--     created_at timestamp with time zone NOT NULL default now()
-- );
-- CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);
-- CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
-- ALTER TABLE users ALTER COLUMN password_hash SET NOT NULL;
-- ALTER TABLE users DROP COLUMN keycloak_sub;
--
-- DELETE FROM flyway_schema_history WHERE version = '1.19';
