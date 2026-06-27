-- The application connects as a superuser, and superusers BYPASS RLS even with FORCE. So each request assumes this
-- dedicated, privilege-limited role via `SET LOCAL ROLE app_rls` (in TenantTransactionFilter) before touching any
-- tenant data; that role is subject to the policies below. Flyway keeps migrating as the superuser.

-- NOLOGIN: only ever assumed via SET ROLE, never connected to directly. NOBYPASSRLS: must obey the policies.
-- Guarded so re-running the migration on an existing cluster is a no-op (CREATE ROLE has no IF NOT EXISTS).
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_rls') THEN
        CREATE ROLE app_rls NOLOGIN NOSUPERUSER NOINHERIT NOBYPASSRLS;
    END IF;
END
$$;

-- Allow the (superuser) login role to assume app_rls. Superusers can already SET ROLE to anything; this also covers
-- a future non-superuser login role.
GRANT app_rls TO CURRENT_USER;

GRANT USAGE ON SCHEMA public TO app_rls;
GRANT SELECT, INSERT, UPDATE, DELETE ON
    systems, toys, video_games, video_game_boxes, video_game_to_video_game_box,
    board_games, board_game_boxes, custom_fields, custom_field_options, custom_field_values, metadata
    TO app_rls;
-- SERIAL ids need the sequences on INSERT. (Referential-integrity checks against users run as the table owner, so
-- app_rls deliberately gets NO grant on users / refresh_tokens — auth + owner resolution run before the demotion.)
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_rls;

-- FORCE so the table owner is covered too. One FOR ALL policy: USING gates which rows are visible/updatable/deletable
-- (owner_id must equal the request's owner), WITH CHECK gates inserted/updated rows (same predicate). owner_id is
-- stamped to the current owner by the column DEFAULT, so writes satisfy WITH CHECK automatically. When the GUC is
-- unset the predicate is NULL -> false: no rows visible, no writes allowed (fail-closed). There is intentionally NO
-- showcase carve-out — an authenticated user sees only their own rows; the anonymous/public build is served the
-- showcase because the request filter resolves an unauthenticated caller to the showcase owner.
ALTER TABLE systems ENABLE ROW LEVEL SECURITY;
ALTER TABLE systems FORCE ROW LEVEL SECURITY;
CREATE POLICY systems_tenant_isolation ON systems
    USING (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int)
    WITH CHECK (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int);

ALTER TABLE toys ENABLE ROW LEVEL SECURITY;
ALTER TABLE toys FORCE ROW LEVEL SECURITY;
CREATE POLICY toys_tenant_isolation ON toys
    USING (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int)
    WITH CHECK (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int);

ALTER TABLE video_games ENABLE ROW LEVEL SECURITY;
ALTER TABLE video_games FORCE ROW LEVEL SECURITY;
CREATE POLICY video_games_tenant_isolation ON video_games
    USING (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int)
    WITH CHECK (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int);

ALTER TABLE video_game_boxes ENABLE ROW LEVEL SECURITY;
ALTER TABLE video_game_boxes FORCE ROW LEVEL SECURITY;
CREATE POLICY video_game_boxes_tenant_isolation ON video_game_boxes
    USING (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int)
    WITH CHECK (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int);

ALTER TABLE video_game_to_video_game_box ENABLE ROW LEVEL SECURITY;
ALTER TABLE video_game_to_video_game_box FORCE ROW LEVEL SECURITY;
CREATE POLICY vg_to_vgbox_tenant_isolation ON video_game_to_video_game_box
    USING (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int)
    WITH CHECK (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int);

ALTER TABLE board_games ENABLE ROW LEVEL SECURITY;
ALTER TABLE board_games FORCE ROW LEVEL SECURITY;
CREATE POLICY board_games_tenant_isolation ON board_games
    USING (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int)
    WITH CHECK (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int);

ALTER TABLE board_game_boxes ENABLE ROW LEVEL SECURITY;
ALTER TABLE board_game_boxes FORCE ROW LEVEL SECURITY;
CREATE POLICY board_game_boxes_tenant_isolation ON board_game_boxes
    USING (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int)
    WITH CHECK (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int);

ALTER TABLE custom_fields ENABLE ROW LEVEL SECURITY;
ALTER TABLE custom_fields FORCE ROW LEVEL SECURITY;
CREATE POLICY custom_fields_tenant_isolation ON custom_fields
    USING (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int)
    WITH CHECK (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int);

ALTER TABLE custom_field_options ENABLE ROW LEVEL SECURITY;
ALTER TABLE custom_field_options FORCE ROW LEVEL SECURITY;
CREATE POLICY custom_field_options_tenant_isolation ON custom_field_options
    USING (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int)
    WITH CHECK (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int);

ALTER TABLE custom_field_values ENABLE ROW LEVEL SECURITY;
ALTER TABLE custom_field_values FORCE ROW LEVEL SECURITY;
CREATE POLICY custom_field_values_tenant_isolation ON custom_field_values
    USING (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int)
    WITH CHECK (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int);

ALTER TABLE metadata ENABLE ROW LEVEL SECURITY;
ALTER TABLE metadata FORCE ROW LEVEL SECURITY;
CREATE POLICY metadata_tenant_isolation ON metadata
    USING (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int)
    WITH CHECK (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int);

-- Undo
-- (per table: DROP POLICY <t>_tenant_isolation ON <t>; ALTER TABLE <t> NO FORCE / DISABLE ROW LEVEL SECURITY;)
-- REVOKE ALL ON ALL TABLES IN SCHEMA public FROM app_rls; REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM app_rls;
-- REVOKE USAGE ON SCHEMA public FROM app_rls; REVOKE app_rls FROM CURRENT_USER; DROP ROLE app_rls;
-- DELETE FROM flyway_schema_history WHERE version = '1.14';
