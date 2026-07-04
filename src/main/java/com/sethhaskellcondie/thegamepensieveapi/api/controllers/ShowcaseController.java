package com.sethhaskellcondie.thegamepensieveapi.api.controllers;

import com.sethhaskellcondie.thegamepensieveapi.api.ApiResponse;
import com.sethhaskellcondie.thegamepensieveapi.api.tenant.OwnerResolver;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Role;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.ShowcaseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The public showcase directory. {@code GET /v1/showcases} lists every <em>visible</em> showcase — an account
 * holding a {@code showcase_slug} whose owner currently derives to PAID or ADMIN, the same visibility rule the
 * {@code X-Showcase} header resolution applies — as slug + display name, backing the front end's showcase
 * switcher. A lapsed owner's showcase drops out of the directory exactly when its slug stops resolving.
 *
 * <p>The route is {@code permitAll} (the directory is anonymous by design) and <strong>bypasses the tenant
 * transaction filter</strong> like the auth/admin routes: it reads the {@code users} table, which the demoted
 * {@code app_rls} role cannot touch. It exposes only slug + name — never emails or account fields (see
 * {@link ShowcaseDto}).
 */
@RestController
@RequestMapping("v1/showcases")
public class ShowcaseController extends BaseController {

    private final UserRepository userRepository;
    private final OwnerResolver ownerResolver;

    public ShowcaseController(UserRepository userRepository, OwnerResolver ownerResolver) {
        this.userRepository = userRepository;
        this.ownerResolver = ownerResolver;
    }

    @ResponseBody
    @GetMapping
    public ApiResponse<List<ShowcaseDto>> listShowcases(HttpServletRequest request) {
        final List<ShowcaseDto> data = userRepository.findAllWithShowcaseSlug().stream()
                .filter(owner -> {
                    final Role ownerRole = ownerResolver.deriveRole(owner);
                    return ownerRole == Role.PAID || ownerRole == Role.ADMIN;
                })
                .map(owner -> new ShowcaseDto(owner.showcaseSlug(), owner.showcaseName()))
                .toList();
        return buildResponse(data, request);
    }
}
