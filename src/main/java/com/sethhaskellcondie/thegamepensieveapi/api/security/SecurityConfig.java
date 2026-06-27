package com.sethhaskellcondie.thegamepensieveapi.api.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Profile-gated security. The default (unsecured) build permits every request so the public showcase keeps
 * working exactly as before. Activating the {@code secured} profile switches on stateless JWT authentication:
 * the heartbeat and the auth endpoints stay public, everything else requires a valid Bearer access token.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/v1/heartbeat",
        "/v1/auth/register",
        "/v1/auth/login",
        "/v1/auth/refresh",
    };

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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

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
            JwtService jwtService,
            UserDetailsService userDetailsService,
            RestAuthenticationEntryPoint authenticationEntryPoint
    ) throws Exception {
        final JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_READ_BY_ID).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_SEARCH).permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/filters/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
