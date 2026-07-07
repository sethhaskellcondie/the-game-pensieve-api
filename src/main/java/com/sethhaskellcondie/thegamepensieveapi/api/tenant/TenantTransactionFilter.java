package com.sethhaskellcondie.thegamepensieveapi.api.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Wires the per-request tenant boundary. For every tenant-scoped request this filter:
 * <ol>
 *   <li>resolves the owner id (while still running with full privileges, before the demotion below) — an
 *       {@code X-Showcase: <slug>} header scopes the request to that public showcase's owner as GUEST (or is
 *       answered 404 right here when the slug is unknown/not visible), otherwise the authenticated caller
 *       (honoring admin impersonation) or the default showcase when anonymous;</li>
 *   <li>opens a transaction and, on that connection, drops to the restricted {@code app_rls} role and sets the
 *       {@code app.current_owner} session variable — both transaction-local ({@code SET LOCAL} /
 *       {@code set_config(..., true)}) so nothing leaks across pooled connections;</li>
 *   <li>runs the rest of the chain inside that transaction.</li>
 * </ol>
 *
 * <p>Because {@code JdbcTemplate} reuses the thread-bound transactional connection, every repository call and every
 * {@code @Transactional} service method in the request observes the role + owner, and Row-Level Security scopes all
 * reads and writes to that owner. Registered to run after Spring Security's filter chain (so the
 * {@code SecurityContext} is populated) and active in both the default and {@code secured} profiles.
 *
 * <p>The public auth endpoints and heartbeat are skipped: they read/write {@code users}/{@code refresh_tokens},
 * which {@code app_rls} cannot access, and must run with the application's normal privileges.
 */
public class TenantTransactionFilter extends OncePerRequestFilter {

    private final OwnerResolver ownerResolver;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    public TenantTransactionFilter(OwnerResolver ownerResolver, TransactionTemplate transactionTemplate, JdbcTemplate jdbcTemplate) {
        this.ownerResolver = ownerResolver;
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        final String path = request.getRequestURI();
        // /v1/admin/** and the public showcase directory are skipped for the same reason as the auth endpoints:
        // they read (or write) the users table, which the demoted app_rls role cannot access. AdminController
        // authorizes the caller itself; the directory is public and exposes only slug + name.
        return path.startsWith("/v1/auth/") || path.equals("/v1/heartbeat") || path.startsWith("/v1/admin/")
                || path.equals("/v1/showcases");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // Resolve owner AND role here, before the transaction drops to app_rls — that read of the users table
        // needs the application's normal privileges (app_rls has no grant on users). The X-Showcase header is an
        // explicit read-only showcase view and wins for every caller (even authenticated ones, and over
        // X-Act-As-Owner when both are present): the request is scoped to the showcase owner as GUEST. Without
        // it, the X-Act-As-Owner header lets an ADMIN caller act as another user (full act-as); it is ignored
        // for everyone else.
        final String showcaseSlug = request.getHeader("X-Showcase");
        final OwnerContext owner;
        if (showcaseSlug != null && !showcaseSlug.isBlank()) {
            final Optional<OwnerContext> showcase = ownerResolver.resolveShowcase(showcaseSlug.trim());
            if (showcase.isEmpty()) {
                // Written directly: an exception thrown from a servlet filter would bypass the JSON error
                // envelope (the same reason impersonation resolution is lenient). The slug is not echoed back.
                writeShowcaseNotFound(response);
                return;
            }
            owner = showcase.get();
        } else {
            owner = ownerResolver.resolveOwner(request.getHeader("X-Act-As-Owner"));
        }
        final Integer ownerId = owner.ownerId();
        TenantContext.set(ownerId);
        TenantContext.setRole(owner.role());
        TenantContext.setShowcaseView(owner.showcase());
        try {
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.execute("SET LOCAL ROLE app_rls");
                // set_config returns the applied value, so it must be queried, not run as an update.
                jdbcTemplate.queryForObject("SELECT set_config('app.current_owner', ?, true)", String.class, String.valueOf(ownerId));
                try {
                    filterChain.doFilter(request, response);
                } catch (IOException | ServletException e) {
                    // Carry the servlet exception out of the transactional callback so the transaction rolls back;
                    // it is unwrapped below to preserve servlet semantics.
                    throw new TenantFilterException(e);
                }
            });
        } catch (TenantFilterException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof ServletException servletException) {
                throw servletException;
            }
            throw new ServletException(cause);
        } catch (UnexpectedRollbackException ignored) {
            // An inner @Transactional component (e.g. a handled validation failure) marked the request transaction
            // rollback-only. The error response is already written and rolling back is the correct outcome.
        } finally {
            TenantContext.clearShowcaseView();
            TenantContext.clearRole();
            TenantContext.clear();
        }
    }

    /**
     * Answer an unknown (or currently not-visible) X-Showcase slug with a 404 in the standard error envelope.
     * The two cases are deliberately indistinguishable so the response does not leak whether a slug exists.
     */
    private void writeShowcaseNotFound(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("application/json");
        response.getWriter().write("{\"data\":null,\"errors\":[\"No public showcase exists for the requested X-Showcase slug.\"]}");
    }

    /** Internal carrier so a checked servlet exception can escape the transactional callback and trigger a rollback. */
    private static final class TenantFilterException extends RuntimeException {
        private TenantFilterException(Throwable cause) {
            super(cause);
        }
    }
}
