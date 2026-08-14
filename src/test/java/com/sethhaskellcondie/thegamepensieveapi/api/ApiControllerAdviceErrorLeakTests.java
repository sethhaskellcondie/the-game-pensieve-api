package com.sethhaskellcondie.thegamepensieveapi.api;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an unexpected 500 is allowed to say. Neither Spring nor Docker is needed for this — the two generic
 * handlers are ordinary methods, and calling them directly is the clearest way to state the rule.
 *
 * <p>The rule matters because of who can reach these handlers. The public showcase read surface is
 * {@code permitAll}, so an ANONYMOUS caller can drive a query that fails, and the handler used to return
 * {@code e.getMessage()} verbatim. For a Postgres {@code DataAccessException} that message carries the
 * failing SQL, the constraint and column names, and the internal hostname {@code db:5432} — a free schema
 * dump and a free map of the private network, to anyone at all.
 */
public class ApiControllerAdviceErrorLeakTests {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final ApiControllerAdvice advice = new ApiControllerAdvice();

    /** A message shaped like the real thing: SQL, a constraint name, and the internal database host. */
    private static final String LEAKY_MESSAGE =
            "PreparedStatementCallback; bad SQL grammar [SELECT users.email, users.keycloak_sub FROM users "
                    + "WHERE owner_id = ?]; constraint [uq_users_single_admin]; host db:5432 refused";

    @Test
    void generalException_ReturnsNoneOfTheExceptionMessage() {
        final List<String> errors = errorsFrom(advice.handleGeneralException(new Exception(LEAKY_MESSAGE)));

        assertNoLeak(errors);
    }

    @Test
    void runtimeException_ReturnsNoneOfTheExceptionMessage() {
        // DataIntegrityViolationException is the concrete shape the audit found reaching anonymous callers.
        final List<String> errors = errorsFrom(
                advice.handleRuntimeException(new DataIntegrityViolationException(LEAKY_MESSAGE)));

        assertNoLeak(errors);
    }

    @Test
    void theResponseCarriesACorrelationIdThatIsDifferentEveryTime() {
        final String first = idFrom(errorsFrom(advice.handleGeneralException(new Exception("boom"))));
        final String second = idFrom(errorsFrom(advice.handleGeneralException(new Exception("boom"))));

        assertTrue(UUID_PATTERN.matcher(first).find(),
                "The caller needs an id to quote when reporting the failure. Was: " + first);
        assertNotEquals(first, second,
                "Two failures must get different ids, or the id cannot identify a log line.");
    }

    @Test
    void aNullExceptionMessageDoesNotBreakTheHandler() {
        // List.of() rejects nulls, so a handler that passed e.getMessage() straight through would throw
        // here — inside the exception handler, which is the worst place for a new exception.
        final List<String> errors = errorsFrom(advice.handleGeneralException(new Exception()));

        assertEquals(2, errors.size(), "A message-less exception still produces the fixed message plus an id.");
    }

    private void assertNoLeak(List<String> errors) {
        final String joined = String.join(" ", errors);
        assertFalse(joined.contains("SELECT"), "The failing SQL must not reach the caller. Was: " + joined);
        assertFalse(joined.contains("users"), "Table and column names must not reach the caller. Was: " + joined);
        assertFalse(joined.contains("uq_users_single_admin"),
                "Constraint names must not reach the caller. Was: " + joined);
        assertFalse(joined.contains("db:5432"),
                "Internal hostnames map the private network and must not reach the caller. Was: " + joined);
        assertTrue(UUID_PATTERN.matcher(joined).find(),
                "The detail is withheld, so a reference id has to take its place. Was: " + joined);
    }

    private String idFrom(List<String> errors) {
        return String.join(" ", errors);
    }

    @SuppressWarnings("unchecked")
    private List<String> errorsFrom(Map<String, List<String>> body) {
        return (List<String>) body.get("errors");
    }
}
