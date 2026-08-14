package com.sethhaskellcondie.thegamepensieveapi.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Refuses a request whose declared body size is over the cap, with <strong>413 Payload Too Large</strong>,
 * before a single byte is deserialized.
 *
 * <p><strong>Why this exists.</strong> {@code POST /v1/function/import} takes an
 * {@code @RequestBody Map<String, Object>}, which Jackson materializes into the heap in full before the
 * controller method is entered. Spring Boot has no body-size setting that covers it:
 * {@code server.tomcat.max-http-form-post-size} applies only to {@code application/x-www-form-urlencoded}
 * bodies and {@code spring.servlet.multipart.*} only to multipart, so a large JSON document has, until now,
 * had no ceiling at all. On a 4 GB host running a 1 GB-capped JVM alongside two databases and Keycloak,
 * one oversized upload is an out-of-memory kill of the backend — which is a denial of service that costs
 * the attacker one request.
 *
 * <p><strong>What it checks, and what it does not.</strong> It reads {@code Content-Length} and rejects
 * anything larger than the cap. It deliberately does not wrap the input stream to count bytes as they
 * arrive, which would be the only way to catch a chunked request that declares no length: the limit would
 * then trip in the middle of Jackson's read, and Spring would bury the failure inside
 * {@code HttpMessageNotReadableException}, turning a clean 413 into a 500 with a misleading message. The
 * chunked case is covered where it should be — at the edge. Caddy's {@code request_body max_size} (see the
 * repo-root {@code Caddyfile}) enforces on bytes actually received, so in production the two together are
 * complete: Caddy stops what it can see arriving, this stops what a client claims to be sending. This
 * filter is the inner of the two and the one that still applies when the app is run without Caddy.
 *
 * <p>Registered with a high precedence so it runs before the security chain: there is no reason to
 * authenticate a request that is going to be refused on size, and no reason to let one reach the point
 * where its body is read.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestSizeLimitFilter.class);

    private final long maxBodyBytes;

    // Default 10 MB (the largest real collection backup in this repo is ~3.3 MB, so ~3x headroom). The
    // property is declared in application.properties; the literal here is the fallback if it is ever removed.
    public RequestSizeLimitFilter(@Value("${pensieve.max-request-body-bytes:10485760}") long maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        final long declared = request.getContentLengthLong();
        if (declared > maxBodyBytes) {
            // Logged at WARN, not ERROR: a refused oversized upload is the filter working, not a fault. The
            // path is included because an operator's first question is always "which endpoint".
            LOGGER.warn("Refused a request body of {} bytes on {} {} (cap is {} bytes)",
                    declared, request.getMethod(), request.getRequestURI(), maxBodyBytes);
            writeTooLarge(response);
            return;
        }
        chain.doFilter(request, response);
    }

    // Hand-written rather than routed through ApiControllerAdvice: a filter runs OUTSIDE the DispatcherServlet,
    // so @ControllerAdvice never sees an exception thrown here. The shape matches FormattedResponseBody's
    // error form ({"data": null, "errors": [...]}) so clients parse it exactly like every other error.
    private void writeTooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"data\":null,\"errors\":[\"Request body is too large. The maximum is "
                        + maxBodyBytes + " bytes.\"]}");
    }
}
