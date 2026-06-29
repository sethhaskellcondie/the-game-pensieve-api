package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

import com.sethhaskellcondie.thegamepensieveapi.api.tenant.TenantContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * The role-based access model's single decision point. Holds the capability matrix — which {@link Role} may
 * perform which {@link Capability} — and exposes {@link #currentRole()} and {@link #can(Capability)} so domain
 * code gates behavior without testing roles directly. The current role is resolved once per request by
 * {@code OwnerResolver} and stashed in {@link TenantContext} <em>before</em> the connection drops to
 * {@code app_rls}; this service only reads that request-scoped value, so it never touches the database and is
 * safe to call from inside the demoted transaction.
 *
 * <p>The access model is a {@code secured} (hosted, multi-tenant) concern. In the default permit-all build there
 * is no authentication and every request resolves to the showcase owner, so enforcement is disabled there and
 * {@link #can(Capability)} reports {@code true} for everything — the single-user public build keeps its
 * unrestricted read/write behavior.
 *
 * <p><strong>Phase 1 (parity):</strong> the matrix reproduces today's behavior. Only {@code FILTER} and
 * {@code WRITE} are enforced at a chokepoint yet, and for those GUEST/PAID/LAPSED match the prior
 * entitlement checks while TRIAL and ADMIN are equivalent to PAID. The remaining rows/capabilities are pinned
 * to their final values in their own later phases (and only then wired to a chokepoint), so there is no
 * observable change this phase.
 */
@Service
public class AccessService {

    // The capability matrix — the single source of truth for what each role may do.
    private static final Map<Role, Set<Capability>> MATRIX = new EnumMap<>(Role.class);

    static {
        MATRIX.put(Role.GUEST, Set.of(Capability.FILTER));
        MATRIX.put(Role.TRIAL, Set.of(Capability.FILTER, Capability.WRITE, Capability.BACKUP, Capability.IMPORT));
        MATRIX.put(Role.PAID, Set.of(Capability.FILTER, Capability.WRITE, Capability.BACKUP, Capability.IMPORT));
        MATRIX.put(Role.LAPSED, Set.of(Capability.BACKUP));
        MATRIX.put(Role.ADMIN, Set.of(Capability.FILTER, Capability.WRITE, Capability.BACKUP, Capability.IMPORT));
    }

    private final boolean enforcementEnabled;

    public AccessService(Environment environment) {
        this.enforcementEnabled = environment.acceptsProfiles(Profiles.of("secured"));
    }

    public Role currentRole() {
        final Role role = TenantContext.getRole();
        return role != null ? role : Role.GUEST;
    }

    /**
     * Whether the current request's role holds the given capability. When enforcement is off (the default
     * permit-all build) every caller has full access, so this always returns {@code true}.
     */
    public boolean can(Capability capability) {
        if (!enforcementEnabled) {
            return true;
        }
        return MATRIX.getOrDefault(currentRole(), Set.of()).contains(capability);
    }
}
