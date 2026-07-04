package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import com.sethhaskellcondie.thegamepensieveapi.api.tenant.OwnerContext;
import com.sethhaskellcondie.thegamepensieveapi.api.tenant.OwnerResolver;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.AccessService;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.AdminUserDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Capability;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Role;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.SetRoleOverrideRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.SetShowcaseRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.User;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.UserRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionForbidden;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInputValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin role-management API. Lists accounts and pins/clears a user's {@code role_override} so an admin can
 * override the auto-derived role.
 *
 * <p><strong>Exactly one admin.</strong> The {@code uq_users_single_admin} partial unique index allows at most
 * one account pinned {@code role_override='ADMIN'} — the operator. Pinning a second admin is rejected with 400
 * ("An admin already exists..."); manual SQL fails hard at the same index. Self-demotion of the only admin is
 * allowed (no lockout logic) — the documented SQL bootstrap below is the recovery path.
 *
 * <p>These routes <strong>bypass the tenant transaction filter</strong> (see
 * {@code TenantTransactionFilter#shouldNotFilter}): they read and write the {@code users} table, which the
 * demoted {@code app_rls} role cannot touch, so they must run with the application's normal privileges — the
 * same reason the auth endpoints are skipped. Because the filter is skipped, {@code TenantContext} is never
 * populated here, so authorization is enforced <em>inside</em> the controller: the caller's role is resolved
 * explicitly via {@link OwnerResolver#resolveOwner()} (which reads {@code users} with app privileges) and any
 * non-ADMIN caller is rejected with 403. Spring Security already requires authentication for {@code /v1/admin/**}
 * under the secured profile, so an anonymous caller is rejected with 401 before reaching here.
 *
 * <p><strong>Bootstrap:</strong> there is no seed/env admin — the operator <em>claims the seeded default-showcase
 * row</em> ({@code showcase@internal.local}, marked {@code is_public_showcase}) with a one-time SQL update that
 * sets a real email, a real BCrypt hash (minted via a throwaway registration — see {@code documentation/Notes.md}
 * for the full procedure), and the admin pin:
 * {@code UPDATE users SET email='you@domain.com', password_hash='<bcrypt>', role_override='ADMIN' WHERE is_public_showcase;}
 * The claimed row is simultaneously the single ADMIN, the default showcase's owner, and an ordinary data owner —
 * the admin logs in and edits the default showcase as their own collection. This is also the recovery path after
 * a self-demotion.
 *
 * <p><strong>Impersonation.</strong> An {@code X-Act-As-Owner: <userId>} request header, honored only when the
 * authenticated caller resolves to ADMIN, switches the acting owner to the target user in
 * {@link OwnerResolver#resolveOwner(String)} (and thus the RLS {@code app.current_owner} set by
 * {@code TenantTransactionFilter}). It is <em>full act-as</em>: the request adopts the target's effective role,
 * so the capability matrix scopes the admin to exactly what that user could do (impersonating a PAID user allows
 * WRITE/BACKUP/IMPORT; a LAPSED user does not). The header is ignored for non-admins and on these admin routes
 * themselves (which authorize via the no-arg {@link OwnerResolver#resolveOwner()}), so an admin cannot lock
 * themselves out of the admin API while impersonating. {@code GET /v1/auth/me} reports the admin as the primary
 * identity with an {@code impersonating} marker naming the target, so the front end knows an admin is driving;
 * the admin stops by no longer sending the header.
 */
@RestController
@RequestMapping("v1/admin")
public class AdminController extends BaseController {

    private final OwnerResolver ownerResolver;
    private final UserRepository userRepository;
    private final AccessService accessService;

    public AdminController(OwnerResolver ownerResolver, UserRepository userRepository, AccessService accessService) {
        this.ownerResolver = ownerResolver;
        this.userRepository = userRepository;
        this.accessService = accessService;
    }

    @ResponseBody
    @GetMapping("/users")
    public ApiResponse<List<AdminUserDto>> listUsers(HttpServletRequest request) {
        requireAdmin();
        final List<AdminUserDto> data = userRepository.findAll().stream().map(this::toDto).toList();
        return buildResponse(data, request);
    }

    @ResponseBody
    @PostMapping("/users/{id}/role")
    public ApiResponse<AdminUserDto> setRoleOverride(
            @PathVariable int id, @RequestBody SetRoleOverrideRequestDto requestBody, HttpServletRequest request) {
        requireAdmin();
        final String roleOverride = normalizeRoleOverride(requestBody == null ? null : requestBody.roleOverride());
        try {
            if (userRepository.updateRoleOverride(id, roleOverride) == 0) {
                throw new ExceptionResourceNotFound("User", id);
            }
        } catch (DuplicateKeyException e) {
            // The uq_users_single_admin partial unique index: at most one account may be pinned ADMIN.
            throw new ExceptionInputValidation("An admin already exists. Clear the current admin's role override first.");
        }
        final AdminUserDto updated = userRepository.findById(id).map(this::toDto)
                .orElseThrow(() -> new ExceptionResourceNotFound("User", id));
        return buildResponse(updated, request);
    }

    /**
     * Grant or revoke a user's public showcase. A non-blank {@code slug} publishes the user's collection at that
     * address (visible while they derive to PAID/ADMIN) with {@code name} as its directory title; a null/blank
     * {@code slug} clears the grant (and the name with it). The slug is the entitlement — this is the
     * backend-authoritative grant an eventual Paddle showcase product would automate.
     */
    @ResponseBody
    @PostMapping("/users/{id}/showcase")
    public ApiResponse<AdminUserDto> setShowcase(
            @PathVariable int id, @RequestBody SetShowcaseRequestDto requestBody, HttpServletRequest request) {
        requireAdmin();
        final String slug = normalizeSlug(requestBody == null ? null : requestBody.slug());
        final String name = requestBody == null ? null : requestBody.name();
        try {
            if (userRepository.updateShowcase(id, slug, name) == 0) {
                throw new ExceptionResourceNotFound("User", id);
            }
        } catch (DuplicateKeyException e) {
            // showcase_slug is UNIQUE — the address is already taken by another user.
            throw new ExceptionInputValidation("The showcase slug '" + slug + "' is already taken.");
        }
        final AdminUserDto updated = userRepository.findById(id).map(this::toDto)
                .orElseThrow(() -> new ExceptionResourceNotFound("User", id));
        return buildResponse(updated, request);
    }

    /** Reject any caller that does not resolve to ADMIN (TenantContext is unavailable on these bypassed routes). */
    private void requireAdmin() {
        final OwnerContext caller = ownerResolver.resolveOwner();
        if (!accessService.can(caller.role(), Capability.ACCESS_ADMIN)) {
            throw new ExceptionForbidden("Admin access required.");
        }
    }

    /** A blank override clears the pin; a non-blank value must name one of the five roles. */
    private String normalizeRoleOverride(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String trimmed = value.trim();
        try {
            Role.valueOf(trimmed);
        } catch (IllegalArgumentException e) {
            throw new ExceptionInputValidation(
                    "Invalid role: '" + value + "'. Must be one of GUEST, TRIAL, PAID, LAPSED, ADMIN, or null to clear.");
        }
        return trimmed;
    }

    /** A blank slug clears the grant; a non-blank value must be lowercase alphanumerics with single interior hyphens. */
    private String normalizeSlug(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String trimmed = value.trim();
        if (!trimmed.matches("^[a-z0-9](-?[a-z0-9])*$")) {
            throw new ExceptionInputValidation("Invalid showcase slug: '" + value
                    + "'. Use lowercase letters, digits, and single interior hyphens (e.g. 'my-collection'), or null to clear.");
        }
        return trimmed;
    }

    private AdminUserDto toDto(User user) {
        return new AdminUserDto(user.id(), user.email(), ownerResolver.deriveRole(user),
                user.roleOverride(), user.accessUntil(), user.subscriptionStatus(),
                user.showcaseSlug(), user.showcaseName());
    }
}
