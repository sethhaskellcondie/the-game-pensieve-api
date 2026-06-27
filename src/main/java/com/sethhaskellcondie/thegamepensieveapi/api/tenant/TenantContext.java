package com.sethhaskellcondie.thegamepensieveapi.api.tenant;

import com.sethhaskellcondie.thegamepensieveapi.domain.auth.EntitlementStatus;

/**
 * Holds the resolved owner id and entitlement status for the duration of a request on the current thread.
 * Populated by {@link TenantTransactionFilter} and cleared in its {@code finally}. The authoritative tenant
 * boundary is the database (the {@code app.current_owner} session variable + Row-Level Security); this holder is
 * a convenience for application code that needs the current owner/status without re-deriving it from the
 * {@code SecurityContext} — and, crucially, the entitlement status is captured here <em>before</em> the request
 * drops to {@code app_rls}, which cannot read {@code users}.
 *
 * <p>Plain {@link ThreadLocal}s are correct under virtual threads: each request runs on its own (virtual) thread
 * with its own thread-locals, and both values are always removed at the end of the request.
 */
public final class TenantContext {

    private static final ThreadLocal<Integer> OWNER = new ThreadLocal<>();
    private static final ThreadLocal<EntitlementStatus> STATUS = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Integer ownerId) {
        OWNER.set(ownerId);
    }

    public static Integer get() {
        return OWNER.get();
    }

    public static void clear() {
        OWNER.remove();
    }

    public static void setStatus(EntitlementStatus status) {
        STATUS.set(status);
    }

    public static EntitlementStatus getStatus() {
        return STATUS.get();
    }

    public static void clearStatus() {
        STATUS.remove();
    }
}
