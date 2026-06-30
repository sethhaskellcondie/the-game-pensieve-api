package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import com.sethhaskellcondie.thegamepensieveapi.api.tenant.OwnerContext;
import com.sethhaskellcondie.thegamepensieveapi.api.tenant.OwnerResolver;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.AuthResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.AuthService;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Impersonation;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.LoginRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.MeResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.RefreshRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.RegisterRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.RegisterResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Role;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.User;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.UserRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints. Unlike the catalog controllers these take a flat request body (there is no
 * entity-key wrapper) and are permitted without a token under the secured profile so callers can obtain one.
 */
@RestController
@RequestMapping("v1/auth")
public class AuthController extends BaseController {

    private final AuthService authService;
    private final OwnerResolver ownerResolver;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, OwnerResolver ownerResolver, UserRepository userRepository) {
        this.authService = authService;
        this.ownerResolver = ownerResolver;
        this.userRepository = userRepository;
    }

    @ResponseBody
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterResponseDto> register(@RequestBody RegisterRequestDto requestDto, HttpServletRequest request) throws ExceptionFailedDbValidation {
        final RegisterResponseDto responseDto = authService.register(requestDto);
        return buildResponse(responseDto, request);
    }

    @ResponseBody
    @PostMapping("/login")
    public ApiResponse<AuthResponseDto> login(@RequestBody LoginRequestDto requestDto, HttpServletRequest request) {
        final AuthResponseDto responseDto = authService.login(requestDto);
        return buildResponse(responseDto, request);
    }

    @ResponseBody
    @PostMapping("/refresh")
    public ApiResponse<AuthResponseDto> refresh(@RequestBody RefreshRequestDto requestDto, HttpServletRequest request) {
        final AuthResponseDto responseDto = authService.refresh(requestDto);
        return buildResponse(responseDto, request);
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
            final Impersonation impersonating = new Impersonation(actingUser.id(), actingUser.email(), caller.role());
            responseDto = new MeResponseDto(
                    caller.impersonator().adminId(), caller.impersonator().adminEmail(), Role.ADMIN, impersonating);
        } else {
            responseDto = new MeResponseDto(actingUser.id(), actingUser.email(), caller.role());
        }
        return buildResponse(responseDto, request);
    }
}
