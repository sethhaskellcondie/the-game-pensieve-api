-- Public showcases: a showcase is a user's own collection made public. A non-NULL showcase_slug is both the
-- entitlement flag and the public address (viewers switch showcases with an X-Showcase: <slug> header); NULL
-- means the collection is private. showcase_name is the display title shown by the public directory and the
-- front end's switcher, so owner emails are never exposed. Both are granted by hand via the admin API for now
-- (backend-authoritative entitlement); a Paddle product can set the same columns later.
--
-- Visibility is role-gated at resolution time, not here: a slug resolves only while its owner derives to PAID
-- or ADMIN (a lapsed owner's showcase 404s until they renew — the slug/address is kept).
ALTER TABLE users ADD COLUMN showcase_slug VARCHAR UNIQUE; -- NULL = collection is private
ALTER TABLE users ADD COLUMN showcase_name VARCHAR;        -- display title for the directory/switcher
ALTER TABLE users ADD CONSTRAINT chk_users_showcase_slug
    CHECK (showcase_slug IS NULL OR showcase_slug ~ '^[a-z0-9](-?[a-z0-9])*$');

-- The seeded V1_13 showcase row becomes the DEFAULT showcase: is_public_showcase is re-documented as the
-- default-showcase marker (the fallback owner for anonymous no-header requests and the non-secured build; the
-- showcase_owner_id() function and the V1_13 owner_id column defaults read it unchanged). The operator claims
-- this row as the single admin (email/password_hash/role_override) via the documented bootstrap; its slug and
-- name are seeded here so the claim only has to set credentials.
UPDATE users SET showcase_slug = 'seths-collection',
    showcase_name = 'Seth''s Collection' WHERE is_public_showcase;

-- Undo
-- ALTER TABLE users DROP CONSTRAINT chk_users_showcase_slug;
-- ALTER TABLE users DROP COLUMN showcase_name;
-- ALTER TABLE users DROP COLUMN showcase_slug;
--
-- DELETE FROM flyway_schema_history WHERE version = '1.18';
