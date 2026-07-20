package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import com.sethhaskellcondie.thegamepensieveapi.api.tenant.OwnerContext;
import com.sethhaskellcondie.thegamepensieveapi.api.tenant.OwnerResolver;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Impersonation;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.MeResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Role;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.User;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.UserRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller-identity endpoint. Login, registration, and token refresh have moved to Keycloak (the single OAuth 2.1
 * authorization server for web + MCP), so the only route left here reports who the current access token belongs to.
 */
@RestController
@RequestMapping("v1/auth")
public class AuthController extends BaseController {

    private final OwnerResolver ownerResolver;
    private final UserRepository userRepository;

    public AuthController(OwnerResolver ownerResolver, UserRepository userRepository) {
        this.ownerResolver = ownerResolver;
        this.userRepository = userRepository;
    }

    /**
     * Report the current caller's identity and effective role. Like the admin routes this lives on the auth path,
     * which bypasses the tenant filter, so the caller is resolved explicitly via
     * {@link OwnerResolver#resolveOwner(String)} (reading {@code users} with the application's normal privileges).
     * Under the secured profile an anonymous caller is rejected with 401 before reaching here; under the default
     * permit-all build the request resolves to the public showcase owner (GUEST).
     *
     * <p>The {@code X-Act-As-Owner} header is honored when an ADMIN caller sends it: the primary fields report the
     * admin and {@code impersonating} carries the target user being acted as. For everyone else the header is
     * ignored and {@code impersonating} is {@code null}.
     */
    @ResponseBody
    @GetMapping("/me")
    public ApiResponse<MeResponseDto> me(HttpServletRequest request) {
        final OwnerContext caller = ownerResolver.resolveOwner(request.getHeader("X-Act-As-Owner"));
        final User actingUser = userRepository.findById(caller.ownerId())
                .orElseThrow(() -> new ExceptionResourceNotFound("User", caller.ownerId()));
        final MeResponseDto responseDto;
        if (caller.impersonator() != null) {
            // The primary identity is the admin's, so report the admin's own access window (not the target's).
            final User adminUser = userRepository.findById(caller.impersonator().adminId())
                    .orElseThrow(() -> new ExceptionResourceNotFound("User", caller.impersonator().adminId()));
            final Impersonation impersonating = new Impersonation(actingUser.id(), actingUser.email(), caller.role());
            responseDto = new MeResponseDto(
                    caller.impersonator().adminId(), caller.impersonator().adminEmail(), Role.ADMIN,
                    toEpochMillis(adminUser.accessUntil()), impersonating);
        } else {
            responseDto = new MeResponseDto(
                    actingUser.id(), actingUser.email(), caller.role(), toEpochMillis(actingUser.accessUntil()));
        }
        return buildResponse(responseDto, request);
    }

    /** The access window as epoch milliseconds for the wire, or null when the account has no window. */
    private static Long toEpochMillis(Timestamp accessUntil) {
        return accessUntil == null ? null : accessUntil.getTime();
    }
}
