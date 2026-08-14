package com.sethhaskellcondie.thegamepensieveapi.api.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The request body cap. No Spring context and no Docker: the filter is a plain servlet filter and the
 * interesting question — does an oversized body reach the application at all — is answered by whether the
 * chain was invoked.
 *
 * <p>{@code POST /v1/function/import} binds an {@code @RequestBody Map} that Jackson materializes fully into
 * the heap before the controller runs, and Spring Boot has no setting that caps a JSON body
 * ({@code max-http-form-post-size} is form-encoded only, {@code spring.servlet.multipart.*} is multipart
 * only). On a 4 GB host running a 1 GB-capped JVM beside two databases and Keycloak, one oversized upload is
 * an out-of-memory kill that costs the attacker a single request.
 */
public class RequestSizeLimitFilterTests {

    private static final long MAX = 1024;

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(MAX);

    @Test
    void aBodyOverTheCapIsRefusedWithoutReachingTheApplication() throws Exception {
        final MockHttpServletRequest request = importRequest(MAX + 1);
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus(), "An oversized body is refused with 413 Payload Too Large.");
        assertNull(chain.getRequest(),
                "The chain must not be invoked — the whole point is that the body is never deserialized.");
    }

    @Test
    void theRefusalIsJsonInTheSameShapeAsEveryOtherError() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(importRequest(MAX * 100), response, new MockFilterChain());

        assertTrue(response.getContentType().startsWith("application/json"),
                "Clients parse errors as JSON; a filter-written body is no exception. Was: " + response.getContentType());
        final String body = response.getContentAsString();
        assertTrue(body.contains("\"data\":null"), "Matches FormattedResponseBody's error form. Was: " + body);
        assertTrue(body.contains("\"errors\""), "Matches FormattedResponseBody's error form. Was: " + body);
        assertTrue(body.contains(String.valueOf(MAX)),
                "The message names the actual cap, so a caller knows what to fit under. Was: " + body);
    }

    @Test
    void aBodyExactlyAtTheCapIsAllowedThrough() throws Exception {
        // Off-by-one matters here: the cap is sized from the largest legitimate payload, so rejecting a body
        // of exactly that size would reject the case it was measured against.
        final MockFilterChain chain = new MockFilterChain();
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(importRequest(MAX), response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest(), "A body exactly at the cap is not over the cap.");
    }

    @Test
    void anOrdinaryRequestIsUntouched() throws Exception {
        final MockFilterChain chain = new MockFilterChain();

        filter.doFilter(importRequest(64), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest(), "A small body passes straight through.");
    }

    @Test
    void aRequestWithNoBodyPassesThrough() throws Exception {
        // getContentLengthLong() is -1 when no length is declared — a GET, or a chunked upload. -1 must not
        // be read as "under the cap by luck" on one hand or refused on the other; it simply is not this
        // filter's case. Chunked bodies are capped at the edge by Caddy's request_body max_size.
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/systems");
        final MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals(-1, request.getContentLengthLong(), "Precondition: no declared length.");
        assertNotNull(chain.getRequest(), "A request with no declared body length is passed on.");
    }

    @Test
    void theCapIsConfigurable() throws Exception {
        final FilterChain chain = new MockFilterChain();
        final MockHttpServletResponse response = new MockHttpServletResponse();

        new RequestSizeLimitFilter(10).doFilter(importRequest(11), response, chain);

        assertEquals(413, response.getStatus(),
                "pensieve.max-request-body-bytes has to actually move the limit — Caddy's edge cap is set "
                        + "from the same number and the two are meant to be raised together.");
    }

    private MockHttpServletRequest importRequest(long contentLength) {
        final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/function/import");
        request.setContentType("application/json");
        // MockHttpServletRequest answers getContentLengthLong() from the content it holds, NOT from a
        // Content-Length header, so the body has to actually be set. Sizes here are small enough for that.
        request.setContent(new byte[(int) contentLength]);
        return request;
    }
}
