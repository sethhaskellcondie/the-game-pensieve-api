package com.sethhaskellcondie.thegamepensieveapi.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads a {@code Bearer} access token off the request, and on a valid token populates the SecurityContext so
 * downstream authorization grants access. An absent or invalid token leaves the request unauthenticated, which
 * the authorization rules then reject via the configured {@link RestAuthenticationEntryPoint}.
 * <p>
 * Constructed directly by the secured SecurityFilterChain (not a Spring bean) so Spring Boot does not also
 * auto-register it as a servlet filter outside the security chain.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        final String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX) && SecurityContextHolder.getContext().getAuthentication() == null) {
            final String token = header.substring(BEARER_PREFIX.length());
            try {
                final String email = jwtService.extractSubject(token);
                if (email != null) {
                    final UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                    final UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception invalidToken) {
                // Invalid/expired token or unknown user: stay unauthenticated and let the entry point return 401.
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
