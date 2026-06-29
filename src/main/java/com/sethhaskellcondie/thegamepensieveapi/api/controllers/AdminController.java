package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import com.sethhaskellcondie.thegamepensieveapi.api.tenant.OwnerContext;
import com.sethhaskellcondie.thegamepensieveapi.api.tenant.OwnerResolver;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.AccessService;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.AdminUserDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Capability;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Role;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.SetRoleOverrideRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.User;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.UserRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionForbidden;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInputValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import jakarta.servlet.http.HttpServletRequest;
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
 * <p>These routes <strong>bypass the tenant transaction filter</strong> (see
 * {@code TenantTransactionFilter#shouldNotFilter}): they read and write the {@code users} table, which the
 * demoted {@code app_rls} role cannot touch, so they must run with the application's normal privileges — the
 * same reason the auth endpoints are skipped. Because the filter is skipped, {@code TenantContext} is never
 * populated here, so authorization is enforced <em>inside</em> the controller: the caller's role is resolved
 * explicitly via {@link OwnerResolver#resolveOwner()} (which reads {@code users} with app privileges) and any
 * non-ADMIN caller is rejected with 403. Spring Security already requires authentication for {@code /v1/admin/**}
 * under the secured profile, so an anonymous caller is rejected with 401 before reaching here.
 *
 * <p><strong>Bootstrap:</strong> there is no seed/env admin — promote the first admin with a one-line SQL
 * update: {@code UPDATE users SET role_override='ADMIN' WHERE email='you@domain.com';}
 *
 * <p><strong>Impersonation — deferred (documented design, not implemented).</strong> A future
 * {@code X-Act-As-Owner: <userId>} request header, honored only when the authenticated caller resolves to
 * ADMIN, would switch the RLS {@code app.current_owner} to the target user in {@code OwnerResolver}/
 * {@code TenantTransactionFilter} and pin a <em>read-only</em> capability set (READ + FILTER only; WRITE,
 * BACKUP, and IMPORT → 403) so an admin can view another user's collection without being able to mutate it.
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
        if (userRepository.updateRoleOverride(id, roleOverride) == 0) {
            throw new ExceptionResourceNotFound("User", id);
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

    private AdminUserDto toDto(User user) {
        return new AdminUserDto(user.id(), user.email(), ownerResolver.deriveRole(user),
                user.roleOverride(), user.accessUntil(), user.subscriptionStatus());
    }
}
