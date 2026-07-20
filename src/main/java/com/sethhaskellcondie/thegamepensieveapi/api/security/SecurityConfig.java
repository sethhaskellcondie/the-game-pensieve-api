package com.sethhaskellcondie.thegamepensieveapi.api.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Profile-gated security. The default (unsecured) build permits every request so the public showcase keeps
 * working exactly as before. Activating the {@code secured} profile turns the app into a stateless OAuth2
 * resource server: it validates Keycloak RS256 access tokens (signature via JWKS, plus {@code iss} + {@code aud};
 * see {@link OAuth2ResourceServerConfig}). The heartbeat and the public showcase read surface stay open;
 * everything else requires a valid Bearer access token.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/v1/heartbeat",
    };

    // The public showcase directory (GET only): anonymous viewers list the visible showcases to switch between
    // them with the X-Showcase header. Exposes only slug + display name.
    private static final String PUBLIC_SHOWCASE_DIRECTORY = "/v1/showcases";

    // Guest (anonymous => showcase owner) read surface. Reads of a single resource and filtered searches are
    // opened so the public showcase is browsable without a token; writes stay authenticated (anonymous => 401).
    // Enumerated per entity (rather than a /v1/*/... wildcard) so future non-entity routes aren't exposed.
    private static final String[] PUBLIC_READ_BY_ID = {
        "/v1/systems/*",
        "/v1/toys/*",
        "/v1/videoGames/*",
        "/v1/videoGameBoxes/*",
        "/v1/boardGames/*",
        "/v1/boardGameBoxes/*",
    };

    private static final String[] PUBLIC_SEARCH = {
        "/v1/systems/function/search",
        "/v1/toys/function/search",
        "/v1/videoGames/function/search",
        "/v1/videoGameBoxes/function/search",
        "/v1/boardGames/function/search",
        "/v1/boardGameBoxes/function/search",
    };

    // Per-entity counts (GET only) join the guest read surface: they summarize exactly the rows the public
    // search endpoints above already expose (RLS/X-Showcase-scoped, soft-deleted excluded), so opening them
    // adds no reach a guest doesn't already have.
    private static final String PUBLIC_COUNTS = "/v1/function/counts";

    // Custom-field definitions (GET only) join the guest read surface: the public showcase renders its owner's
    // custom-field columns from these, so — like the entity read/search/filters routes — they must be reachable
    // without a token and are X-Showcase-scoped by the tenant filter + RLS. Writes (POST/PUT/DELETE) are not
    // listed, so they fall through to authenticated() and a GUEST showcase view is rejected in the gateway.
    private static final String[] PUBLIC_CUSTOM_FIELDS_READ = {
        "/v1/custom_fields",
        "/v1/custom_fields/entity/*",
    };

    // The metadata keys the public showcase renders from (GET only) join the guest read surface. A showcase view is
    // served the fixed guest ui-settings and reads the owner's own default_sort_options, saved-filters, and
    // saved-filter-categories (mirrored via RLS), so a guest sees the owner's configured showcase — all must be
    // reachable without a token. Only these keys are opened (not a /v1/metadata/* wildcard, nor the list-all GET) so
    // a showcase visitor can't enumerate the owner's other metadata. Writes (POST/PATCH/DELETE) are not listed, so
    // they fall through to authenticated() and a GUEST showcase view is rejected in the gateway.
    private static final String[] PUBLIC_METADATA_READ = {
        "/v1/metadata/ui-settings",
        "/v1/metadata/default_sort_options",
        "/v1/metadata/saved-filters",
        "/v1/metadata/saved-filter-categories",
    };

    @Bean
    @Profile("!secured")
    public SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Profile("secured")
    public SecurityFilterChain securedFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_READ_BY_ID).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_SEARCH).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_COUNTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_CUSTOM_FIELDS_READ).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_METADATA_READ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/filters/**").permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_SHOWCASE_DIRECTORY).permitAll()
                        .anyRequest().authenticated())
                // The secured chain is an OAuth2 resource server: it validates Keycloak RS256 access tokens
                // (signature via JWKS, plus iss + aud) using the JwtDecoder bean from OAuth2ResourceServerConfig.
                // The default JwtAuthenticationToken carries the token's claims (sub, email) for OwnerResolver.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> { }))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint));
        return http.build();
    }
}
