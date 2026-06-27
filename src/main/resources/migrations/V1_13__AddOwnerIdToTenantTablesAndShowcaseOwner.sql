-- A single, world-readable account that owns all pre-existing data and is served to the anonymous/public build.
-- Marked by a flag; the partial unique index guarantees at most one. Its password hash is unusable, so it can
-- never be logged into.
ALTER TABLE users ADD COLUMN is_public_showcase BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX uq_users_single_showcase ON users (is_public_showcase) WHERE is_public_showcase;

INSERT INTO users (email, password_hash, enabled, is_public_showcase)
VALUES ('showcase@internal.local', '!', TRUE, TRUE);

-- the showcase owner's id is stored in the database and can be retrieved as needed.
CREATE FUNCTION showcase_owner_id() RETURNS INTEGER
    LANGUAGE sql STABLE AS $$ SELECT id FROM users WHERE is_public_showcase $$;

-- For each table: add the column nullable, backfill existing rows to the showcase owner, then make it NOT NULL with
-- a DEFAULT that stamps the current request's owner from the app.current_owner session variable (set per request by
-- TenantTransactionFilter). current_setting(..., true) is missing_ok so the migration and GUC-less inserts don't
-- error; COALESCE falls back to the showcase owner when the GUC is unset.

-- systems
ALTER TABLE systems ADD COLUMN owner_id INTEGER;
UPDATE systems SET owner_id = showcase_owner_id() WHERE owner_id IS NULL;
ALTER TABLE systems
    ALTER COLUMN owner_id SET NOT NULL,
    ALTER COLUMN owner_id SET DEFAULT COALESCE(NULLIF(current_setting('app.current_owner', true), '')::int, showcase_owner_id()),
    ADD CONSTRAINT fk_systems_owner FOREIGN KEY (owner_id) REFERENCES users (id);
CREATE INDEX idx_systems_owner_id ON systems (owner_id);

-- toys
ALTER TABLE toys ADD COLUMN owner_id INTEGER;
UPDATE toys SET owner_id = showcase_owner_id() WHERE owner_id IS NULL;
ALTER TABLE toys
    ALTER COLUMN owner_id SET NOT NULL,
    ALTER COLUMN owner_id SET DEFAULT COALESCE(NULLIF(current_setting('app.current_owner', true), '')::int, showcase_owner_id()),
    ADD CONSTRAINT fk_toys_owner FOREIGN KEY (owner_id) REFERENCES users (id);
CREATE INDEX idx_toys_owner_id ON toys (owner_id);

-- video_games
ALTER TABLE video_games ADD COLUMN owner_id INTEGER;
UPDATE video_games SET owner_id = showcase_owner_id() WHERE owner_id IS NULL;
ALTER TABLE video_games
    ALTER COLUMN owner_id SET NOT NULL,
    ALTER COLUMN owner_id SET DEFAULT COALESCE(NULLIF(current_setting('app.current_owner', true), '')::int, showcase_owner_id()),
    ADD CONSTRAINT fk_video_games_owner FOREIGN KEY (owner_id) REFERENCES users (id);
CREATE INDEX idx_video_games_owner_id ON video_games (owner_id);

-- video_game_boxes
ALTER TABLE video_game_boxes ADD COLUMN owner_id INTEGER;
UPDATE video_game_boxes SET owner_id = showcase_owner_id() WHERE owner_id IS NULL;
ALTER TABLE video_game_boxes
    ALTER COLUMN owner_id SET NOT NULL,
    ALTER COLUMN owner_id SET DEFAULT COALESCE(NULLIF(current_setting('app.current_owner', true), '')::int, showcase_owner_id()),
    ADD CONSTRAINT fk_video_game_boxes_owner FOREIGN KEY (owner_id) REFERENCES users (id);
CREATE INDEX idx_video_game_boxes_owner_id ON video_game_boxes (owner_id);

-- video_game_to_video_game_box (junction)
ALTER TABLE video_game_to_video_game_box ADD COLUMN owner_id INTEGER;
UPDATE video_game_to_video_game_box SET owner_id = showcase_owner_id() WHERE owner_id IS NULL;
ALTER TABLE video_game_to_video_game_box
    ALTER COLUMN owner_id SET NOT NULL,
    ALTER COLUMN owner_id SET DEFAULT COALESCE(NULLIF(current_setting('app.current_owner', true), '')::int, showcase_owner_id()),
    ADD CONSTRAINT fk_vg_to_vgbox_owner FOREIGN KEY (owner_id) REFERENCES users (id);
CREATE INDEX idx_vg_to_vgbox_owner_id ON video_game_to_video_game_box (owner_id);

-- board_games
ALTER TABLE board_games ADD COLUMN owner_id INTEGER;
UPDATE board_games SET owner_id = showcase_owner_id() WHERE owner_id IS NULL;
ALTER TABLE board_games
    ALTER COLUMN owner_id SET NOT NULL,
    ALTER COLUMN owner_id SET DEFAULT COALESCE(NULLIF(current_setting('app.current_owner', true), '')::int, showcase_owner_id()),
    ADD CONSTRAINT fk_board_games_owner FOREIGN KEY (owner_id) REFERENCES users (id);
CREATE INDEX idx_board_games_owner_id ON board_games (owner_id);

-- board_game_boxes
ALTER TABLE board_game_boxes ADD COLUMN owner_id INTEGER;
UPDATE board_game_boxes SET owner_id = showcase_owner_id() WHERE owner_id IS NULL;
ALTER TABLE board_game_boxes
    ALTER COLUMN owner_id SET NOT NULL,
    ALTER COLUMN owner_id SET DEFAULT COALESCE(NULLIF(current_setting('app.current_owner', true), '')::int, showcase_owner_id()),
    ADD CONSTRAINT fk_board_game_boxes_owner FOREIGN KEY (owner_id) REFERENCES users (id);
CREATE INDEX idx_board_game_boxes_owner_id ON board_game_boxes (owner_id);

-- custom_fields
ALTER TABLE custom_fields ADD COLUMN owner_id INTEGER;
UPDATE custom_fields SET owner_id = showcase_owner_id() WHERE owner_id IS NULL;
ALTER TABLE custom_fields
    ALTER COLUMN owner_id SET NOT NULL,
    ALTER COLUMN owner_id SET DEFAULT COALESCE(NULLIF(current_setting('app.current_owner', true), '')::int, showcase_owner_id()),
    ADD CONSTRAINT fk_custom_fields_owner FOREIGN KEY (owner_id) REFERENCES users (id);
CREATE INDEX idx_custom_fields_owner_id ON custom_fields (owner_id);

-- custom_field_options
ALTER TABLE custom_field_options ADD COLUMN owner_id INTEGER;
UPDATE custom_field_options SET owner_id = showcase_owner_id() WHERE owner_id IS NULL;
ALTER TABLE custom_field_options
    ALTER COLUMN owner_id SET NOT NULL,
    ALTER COLUMN owner_id SET DEFAULT COALESCE(NULLIF(current_setting('app.current_owner', true), '')::int, showcase_owner_id()),
    ADD CONSTRAINT fk_custom_field_options_owner FOREIGN KEY (owner_id) REFERENCES users (id);
CREATE INDEX idx_custom_field_options_owner_id ON custom_field_options (owner_id);

-- custom_field_values
ALTER TABLE custom_field_values ADD COLUMN owner_id INTEGER;
UPDATE custom_field_values SET owner_id = showcase_owner_id() WHERE owner_id IS NULL;
ALTER TABLE custom_field_values
    ALTER COLUMN owner_id SET NOT NULL,
    ALTER COLUMN owner_id SET DEFAULT COALESCE(NULLIF(current_setting('app.current_owner', true), '')::int, showcase_owner_id()),
    ADD CONSTRAINT fk_custom_field_values_owner FOREIGN KEY (owner_id) REFERENCES users (id);
CREATE INDEX idx_custom_field_values_owner_id ON custom_field_values (owner_id);

-- metadata
ALTER TABLE metadata ADD COLUMN owner_id INTEGER;
UPDATE metadata SET owner_id = showcase_owner_id() WHERE owner_id IS NULL;
ALTER TABLE metadata
    ALTER COLUMN owner_id SET NOT NULL,
    ALTER COLUMN owner_id SET DEFAULT COALESCE(NULLIF(current_setting('app.current_owner', true), '')::int, showcase_owner_id()),
    ADD CONSTRAINT fk_metadata_owner FOREIGN KEY (owner_id) REFERENCES users (id);
CREATE INDEX idx_metadata_owner_id ON metadata (owner_id);

-- metadata.key was globally UNIQUE; under multi-tenancy each owner gets its own key namespace.
ALTER TABLE metadata DROP CONSTRAINT metadata_key_key;
ALTER TABLE metadata ADD CONSTRAINT uq_metadata_owner_key UNIQUE (owner_id, key);

-- Undo
-- (reverse per-table: DROP INDEX, DROP CONSTRAINT fk_*, ALTER COLUMN owner_id DROP DEFAULT/NOT NULL, DROP COLUMN)
-- ALTER TABLE metadata DROP CONSTRAINT uq_metadata_owner_key;
-- ALTER TABLE metadata ADD CONSTRAINT metadata_key_key UNIQUE (key);
-- DROP FUNCTION showcase_owner_id();
-- DELETE FROM users WHERE is_public_showcase;
-- DROP INDEX uq_users_single_showcase;
-- ALTER TABLE users DROP COLUMN is_public_showcase;
-- DELETE FROM flyway_schema_history WHERE version = '1.13';
