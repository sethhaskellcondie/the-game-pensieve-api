-- Authentication foundation: account records and revocable refresh tokens.

CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    email VARCHAR NOT NULL UNIQUE, -- doubles as the username/login identifier
    password_hash VARCHAR NOT NULL, -- BCrypt hash, never the plaintext password
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at timestamp with time zone NOT NULL default now(),
    updated_at timestamp with time zone NOT NULL default now()
);

-- Refresh tokens are stored as SHA-256 hashes (never plaintext) so a leaked table can't be replayed.
-- Rows are kept after use/revocation so rotation history is auditable; lookups filter on revoked + expires_at.
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id),
    token_hash VARCHAR NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at timestamp with time zone NOT NULL default now()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- Undo
-- DROP TABLE refresh_tokens;
--
-- DROP TABLE users;
--
-- DELETE FROM flyway_schema_history WHERE version = '1.12';
