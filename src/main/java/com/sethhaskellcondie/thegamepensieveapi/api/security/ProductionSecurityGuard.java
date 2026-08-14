package com.sethhaskellcondie.thegamepensieveapi.api.security;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Refuses to start a production deployment that is not running the {@code secured} profile.
 *
 * <p>The default security posture is permit-all: {@link SecurityConfig#permitAllFilterChain} is
 * {@code @Profile("!secured")} and {@code AccessService} short-circuits every capability check to {@code true}
 * when {@code secured} is absent. That is correct for the single-user public build, and dangerous everywhere
 * else, because the failure is silent — an app that lost {@code secured} from {@code SPRING_PROFILES_ACTIVE}
 * (a bad template, an env file that did not load, a typo in a deploy script) boots green against the real
 * production datasource, logs nothing unusual, serves every endpoint to anonymous callers, and looks healthy on
 * the heartbeat. Nothing about it announces that authentication is gone.
 *
 * <p>So production declares itself with a marker that is deliberately <em>not</em> part of the profile list:
 * {@code PENSIEVE_ENV=production}, set in {@code compose.production.yaml}. If the marker is present and
 * {@code secured} is not active, the context fails to refresh and the container exits. The whole point of using
 * a separate variable is that the slip which drops {@code secured} cannot also disarm the guard — a guard keyed
 * on the profile list would be silenced by exactly the mistake it exists to catch.
 *
 * <p>The check is one-directional on purpose. Running {@code secured} without the marker is fine and common
 * (the dev secured stack, the Testcontainers suite); only the reverse is fatal.
 */
@Component
public class ProductionSecurityGuard {

    static final String ENV_MARKER = "PENSIEVE_ENV";
    static final String PRODUCTION = "production";

    /**
     * The check runs in the constructor so that a failure aborts the context refresh, which is what makes the
     * container exit rather than serve. Spring wraps it in a BeanCreationException; the message below is what
     * reaches the log.
     */
    public ProductionSecurityGuard(Environment environment) {
        assertSecuredInProduction(environment);
    }

    private void assertSecuredInProduction(Environment environment) {
        final String marker = environment.getProperty(ENV_MARKER);
        if (!PRODUCTION.equalsIgnoreCase(marker)) {
            return;
        }
        if (environment.acceptsProfiles(Profiles.of("secured"))) {
            return;
        }
        throw new IllegalStateException(
                ENV_MARKER + "=" + PRODUCTION + " but the 'secured' profile is not active. The default build permits "
                        + "every request and disables all role checks, so this configuration would serve the production "
                        + "database to anonymous callers. Refusing to start. Active profiles: "
                        + String.join(", ", environment.getActiveProfiles().length == 0
                                ? new String[]{"(none — the default profile is in use)"} : environment.getActiveProfiles())
                        + ". Set SPRING_PROFILES_ACTIVE to include 'secured' (production uses 'docker,secured').");
    }
}
