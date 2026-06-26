package com.sethhaskellcondie.thegamepensieveapi.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensieveapi.api.FormattedResponseBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Renders a 401 in the same {@code {"data": null, "errors": [...]}} shape the rest of the API uses, so an
 * unauthenticated request fails consistently rather than with Spring Security's default HTML response.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        final FormattedResponseBody<List<String>> body = new FormattedResponseBody<>(List.of("Authentication required."));
        response.getWriter().write(objectMapper.writeValueAsString(body.formatError()));
    }
}
