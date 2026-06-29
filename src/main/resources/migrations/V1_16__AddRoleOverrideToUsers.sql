-- Role-based access model: an optional admin "pin" that overrides the per-request role derivation.
-- The effective role is normally derived from access_until / subscription_status (see OwnerResolver); when
-- role_override is non-NULL it wins, letting an admin force any user (including ADMIN) to a specific role.
-- NULL => no pin, auto-derive as before. Like the V1_15 billing columns, this is read only during owner/role
-- resolution, which runs with normal privileges BEFORE the per-request app_rls demotion; users is NOT under
-- RLS and app_rls has no grant on it, so nothing about RLS needs to change for this migration.

ALTER TABLE users ADD COLUMN role_override VARCHAR; -- NULL | 'GUEST' | 'TRIAL' | 'PAID' | 'LAPSED' | 'ADMIN'

ALTER TABLE users ADD CONSTRAINT chk_users_role_override
    CHECK (role_override IS NULL OR role_override IN ('GUEST', 'TRIAL', 'PAID', 'LAPSED', 'ADMIN'));

-- Existing rows (including the seeded showcase owner) backfill to NULL, so every account keeps auto-deriving
-- its role. Bootstrap an admin with: UPDATE users SET role_override='ADMIN' WHERE email='you@domain.com';

-- Undo
-- ALTER TABLE users DROP CONSTRAINT chk_users_role_override;
-- ALTER TABLE users DROP COLUMN role_override;
--
-- DELETE FROM flyway_schema_history WHERE version = '1.16';
