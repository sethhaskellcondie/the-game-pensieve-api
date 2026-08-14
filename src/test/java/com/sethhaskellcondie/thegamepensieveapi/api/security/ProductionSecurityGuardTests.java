package com.sethhaskellcondie.thegamepensieveapi.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guard decides whether the application is allowed to start, so it is worth pinning down in a test that
 * needs neither a Spring context nor Docker. The dangerous case is the silent one: a production deployment that
 * lost the 'secured' profile boots permit-all and looks perfectly healthy, which is why the guard exists.
 */
public class ProductionSecurityGuardTests {

    @Test
    void productionWithoutSecuredProfile_RefusesToStart() {
        final MockEnvironment environment = new MockEnvironment();
        environment.setProperty(ProductionSecurityGuard.ENV_MARKER, ProductionSecurityGuard.PRODUCTION);
        environment.setActiveProfiles("docker");

        final IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new ProductionSecurityGuard(environment),
                "A production marker without the secured profile must abort startup, not boot permit-all.");

        assertTrue(thrown.getMessage().contains("secured"),
                "The message has to name the missing profile — it is the only thing telling an operator what to fix. Was: " + thrown.getMessage());
    }

    @Test
    void productionWithSecuredProfile_Starts() {
        final MockEnvironment environment = new MockEnvironment();
        environment.setProperty(ProductionSecurityGuard.ENV_MARKER, ProductionSecurityGuard.PRODUCTION);
        environment.setActiveProfiles("docker", "secured");

        assertDoesNotThrow(() -> new ProductionSecurityGuard(environment));
    }

    /**
     * The permit-all build is a supported product (the single-user public showcase), so the guard must be silent
     * unless production has been declared. Only the marker arms it.
     */
    @Test
    void noProductionMarker_IsAlwaysAllowed() {
        final MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertDoesNotThrow(() -> new ProductionSecurityGuard(environment));
    }

    /**
     * Running secured outside production is normal — the dev secured stack and the Testcontainers suite both do
     * it. The check is one-directional.
     */
    @Test
    void securedWithoutProductionMarker_IsAllowed() {
        final MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("docker", "secured");

        assertDoesNotThrow(() -> new ProductionSecurityGuard(environment));
    }

    /**
     * An empty or unrelated marker value must not arm the guard — otherwise setting PENSIEVE_ENV=staging on a
     * deliberately unsecured box would refuse to boot for no reason.
     */
    @Test
    void nonProductionMarkerValues_DoNotArmTheGuard() {
        final MockEnvironment staging = new MockEnvironment();
        staging.setProperty(ProductionSecurityGuard.ENV_MARKER, "staging");
        staging.setActiveProfiles("docker");

        assertDoesNotThrow(() -> new ProductionSecurityGuard(staging));
    }

    /**
     * The marker arrives as an environment variable typed by a human into an env file, so a capitalised value
     * must not quietly disarm the guard.
     */
    @Test
    void productionMarkerIsCaseInsensitive() {
        final MockEnvironment environment = new MockEnvironment();
        environment.setProperty(ProductionSecurityGuard.ENV_MARKER, "Production");
        environment.setActiveProfiles("docker");

        assertThrows(IllegalStateException.class, () -> new ProductionSecurityGuard(environment));
    }
}
