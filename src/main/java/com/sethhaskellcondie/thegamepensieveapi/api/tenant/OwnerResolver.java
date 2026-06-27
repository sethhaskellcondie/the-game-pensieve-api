package com.sethhaskellcondie.thegamepensieveapi.api.tenant;

import com.sethhaskellcondie.thegamepensieveapi.domain.auth.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves the owner id for the current request from the {@link org.springframework.security.core.context.SecurityContext}.
 *
 * <ul>
 *   <li>An authenticated request → the user's id, looked up by the principal's email.</li>
 *   <li>An anonymous request (the default permit-all build, or before login) → the seeded public showcase owner,
 *       so guests transparently read the showcase.</li>
 * </ul>
 *
 * <p>Resolution runs <em>before</em> the request transaction drops to {@code app_rls}, so the {@code users} lookups
 * here execute with the application's normal (superuser) privileges — {@code app_rls} has no grant on {@code users}.
 */
@Component
public class OwnerResolver {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    // The showcase owner id never changes after V1_13 seeds it; cache the first lookup.
    private final AtomicReference<Integer> showcaseOwnerId = new AtomicReference<>();

    public OwnerResolver(UserRepository userRepository, JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Integer resolveOwnerId() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserDetails userDetails) {
            return userRepository.findByEmail(userDetails.getUsername())
                    .map(user -> user.id())
                    .orElseGet(this::showcaseOwnerId);
        }
        return showcaseOwnerId();
    }

    private Integer showcaseOwnerId() {
        Integer cached = showcaseOwnerId.get();
        if (cached == null) {
            cached = jdbcTemplate.queryForObject("SELECT id FROM users WHERE is_public_showcase", Integer.class);
            showcaseOwnerId.set(cached);
        }
        return cached;
    }
}
