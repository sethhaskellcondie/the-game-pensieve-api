package com.sethhaskellcondie.thegamepensieveapi.api.tenant;

/**
 * Holds the resolved owner id for the duration of a request on the current thread. Populated by
 * {@link TenantTransactionFilter} and cleared in its {@code finally}. The authoritative tenant boundary is the
 * database (the {@code app.current_owner} session variable + Row-Level Security); this holder is a convenience for
 * application code that needs the current owner without re-deriving it from the {@code SecurityContext}.
 *
 * <p>A plain {@link ThreadLocal} is correct under virtual threads: each request runs on its own (virtual) thread
 * with its own thread-locals, and the value is always removed at the end of the request.
 */
public final class TenantContext {

    private static final ThreadLocal<Integer> OWNER = new ThreadLocal<>();

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
}
