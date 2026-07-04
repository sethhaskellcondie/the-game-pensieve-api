-- Exactly one system admin: at most one account may be pinned role_override='ADMIN', mirroring the
-- uq_users_single_showcase pattern. Every matching row holds the same value ('ADMIN'), so uniqueness under the
-- partial predicate means "at most one row". The admin API surfaces the violation as a 400 ("An admin already
-- exists..."); a manual SQL pin fails hard at the index the same way.
--
-- Precondition: this CREATE fails if the data already holds two or more pinned admins. No current environment
-- does; if one ever did, clear the extras first:
--   UPDATE users SET role_override = NULL WHERE role_override = 'ADMIN' AND email <> 'the-keeper@domain.com';
CREATE UNIQUE INDEX uq_users_single_admin ON users (role_override) WHERE role_override = 'ADMIN';

-- Undo
-- DROP INDEX uq_users_single_admin;
--
-- DELETE FROM flyway_schema_history WHERE version = '1.17';
