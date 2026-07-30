package com.sethhaskellcondie.thegamepensieveapi.api.tenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The database side of the per-request tenant boundary. Unlike the other repositories this one owns no table: it
 * configures the <em>session</em> on the current transaction's connection, which is why it lives next to the
 * {@link TenantTransactionFilter} that collaborates with it rather than under {@code domain}.
 *
 * <p>Both statements are transaction-local ({@code SET LOCAL} / {@code set_config(..., true)}), so nothing leaks
 * across pooled connections — a connection handed back to the pool has its normal privileges and no tenant set.
 */
@Repository
public class TenantSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public TenantSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Scope the rest of the current transaction to one owner: drop to the restricted {@code app_rls} role and set
     * the {@code app.current_owner} session variable. Because {@code JdbcTemplate} reuses the thread-bound
     * transactional connection, every repository call made afterwards in the same transaction observes the role +
     * owner, and Row-Level Security scopes all reads and writes to that owner.
     *
     * <p>Must be called on a connection that still has the application's normal privileges — {@code app_rls}
     * cannot re-assume a role, and it has no grant on the {@code users} table.
     */
    public void assumeTenant(int ownerId) {
        jdbcTemplate.execute("SET LOCAL ROLE app_rls");
        // set_config returns the applied value, so it must be queried, not run as an update.
        jdbcTemplate.queryForObject("SELECT set_config('app.current_owner', ?, true)", String.class, String.valueOf(ownerId));
    }
}
