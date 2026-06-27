package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

import com.sethhaskellcondie.thegamepensieveapi.api.tenant.TenantContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

/**
 * Exposes the current request's effective {@link EntitlementStatus} to domain code (the gateways gate writes and
 * filtered searches on it). The status is resolved once per request by {@code OwnerResolver} and stashed in
 * {@link TenantContext} <em>before</em> the connection drops to {@code app_rls}; this service only reads that
 * request-scoped value, so it never touches the database and is safe to call from inside the demoted transaction.
 *
 * <p>The access model is a {@code secured} (hosted, multi-tenant) concern. In the default permit-all build there
 * is no authentication and every request resolves to the showcase owner, so enforcement is disabled there and all
 * gate checks report full access — the single-user public build keeps its unrestricted read/write behavior.
 */
@Service
public class EntitlementService {

    private final boolean enforcementEnabled;

    public EntitlementService(Environment environment) {
        this.enforcementEnabled = environment.acceptsProfiles(Profiles.of("secured"));
    }

    public EntitlementStatus currentStatus() {
        final EntitlementStatus status = TenantContext.getStatus();
        return status != null ? status : EntitlementStatus.GUEST;
    }

    public boolean isGuest() {
        return enforcementEnabled && currentStatus() == EntitlementStatus.GUEST;
    }

    public boolean isPaid() {
        // When enforcement is off (default build) every caller has full access.
        return !enforcementEnabled || currentStatus() == EntitlementStatus.PAID;
    }

    public boolean isLapsed() {
        return enforcementEnabled && currentStatus() == EntitlementStatus.LAPSED;
    }
}
