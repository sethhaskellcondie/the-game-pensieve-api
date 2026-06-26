package com.sethhaskellcondie.thegamepensieveapi.api.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
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
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
