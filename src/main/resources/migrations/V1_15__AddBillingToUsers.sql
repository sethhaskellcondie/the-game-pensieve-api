-- Phase 3: price-agnostic entitlement fields. Effective access is resolved in the application from
-- access_until (NOT from Paddle) — a request is "paid" while access_until is in the future, which is what
-- both a purchase and a trial set. plan + subscription_status are informational / for Paddle reconciliation.
-- users is NOT under RLS and the app_rls role has NO grant on it (see V1_14); these columns are read only
-- during owner/entitlement resolution, which runs with normal privileges BEFORE the per-request app_rls
-- demotion. Nothing about RLS needs to change for this migration.

ALTER TABLE users ADD COLUMN plan VARCHAR NOT NULL DEFAULT 'free'; -- 'free' | 'paid'
ALTER TABLE users ADD COLUMN subscription_status VARCHAR;          -- 'trialing' | 'active' | 'past_due' | 'canceled' | NULL
ALTER TABLE users ADD COLUMN access_until timestamp with time zone; -- NULL => no access window (resolves to LAPSED)
ALTER TABLE users ADD COLUMN paddle_customer_id VARCHAR;           -- nullable, Paddle reconciliation
ALTER TABLE users ADD COLUMN paddle_subscription_id VARCHAR;       -- nullable, Paddle reconciliation
ALTER TABLE users ADD COLUMN last_event_id VARCHAR;                -- nullable, idempotency key for the future Paddle webhook

ALTER TABLE users ADD CONSTRAINT chk_users_plan CHECK (plan IN ('free', 'paid'));
ALTER TABLE users ADD CONSTRAINT chk_users_subscription_status
    CHECK (subscription_status IS NULL OR subscription_status IN ('trialing', 'active', 'past_due', 'canceled'));

-- Existing rows (including the seeded showcase owner) backfill via the column defaults/NULL to plan='free'
-- with no access window. The showcase owner resolves to GUEST through the anonymous path regardless.

-- Undo
-- ALTER TABLE users DROP CONSTRAINT chk_users_subscription_status;
-- ALTER TABLE users DROP CONSTRAINT chk_users_plan;
-- ALTER TABLE users DROP COLUMN last_event_id;
-- ALTER TABLE users DROP COLUMN paddle_subscription_id;
-- ALTER TABLE users DROP COLUMN paddle_customer_id;
-- ALTER TABLE users DROP COLUMN access_until;
-- ALTER TABLE users DROP COLUMN subscription_status;
-- ALTER TABLE users DROP COLUMN plan;
--
-- DELETE FROM flyway_schema_history WHERE version = '1.15';
