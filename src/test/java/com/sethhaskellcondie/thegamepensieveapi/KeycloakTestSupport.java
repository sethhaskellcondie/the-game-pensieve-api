package com.sethhaskellcondie.thegamepensieveapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Owns the single Keycloak Testcontainer that backs the {@code secured}-profile test suites and provides the
 * helpers those tests use to obtain real RS256 access tokens. The container imports the very same realm file the
 * compose stack uses ({@code keycloak/import/pensieve-realm.json}, mounted by host path — no duplicated copy to
 * drift), so the tokens it mints carry the production audience ({@code aud=http://localhost:8090/mcp}), the
 * {@code pensieve:read} scope, and {@code sub}/{@code email} — exactly what the resource server and
 * {@code OwnerResolver} validate.
 *
 * <p>The container is a JVM-wide singleton started once on first use and reused across every test class (Ryuk
 * reaps it at the end of the run); {@link #issuerUri()}/{@link #jwksUri()} feed the resource-server config via
 * {@code @DynamicPropertySource} in {@link SecuredProfileTest}. Tokens are minted with the realm's public
 * {@code pensieve-test-client} through the OAuth2 direct-access (password) grant; {@link #ensureUser} admin-creates
 * a Keycloak account on demand so tests keep their existing "make a user, get a token" shape for arbitrary emails.
 */
public final class KeycloakTestSupport {

    private static final String IMAGE = "quay.io/keycloak/keycloak:26.7";
    private static final String REALM = "pensieve";
    private static final String CLIENT_ID = "pensieve-test-client";
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "admin";

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("resource") // Singleton: reused for the whole JVM test run and reaped by Ryuk, never closed here.
    private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(IMAGE)
            .withExposedPorts(8080)
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", ADMIN_USER)
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", ADMIN_PASS)
            .withCopyFileToContainer(
                    MountableFile.forHostPath("keycloak/import/pensieve-realm.json"),
                    "/opt/keycloak/data/import/pensieve-realm.json")
            .withCommand("start-dev", "--import-realm")
            .waitingFor(Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
                    .forPort(8080)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    static {
        KEYCLOAK.start();
    }

    private KeycloakTestSupport() {
    }

    /** The host-reachable base URL of the running Keycloak (dynamic Testcontainers port). */
    public static String baseUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }

    /** The realm issuer — the exact {@code iss} the minted tokens carry (host-reachable). */
    public static String issuerUri() {
        return baseUrl() + "/realms/" + REALM;
    }

    /** The realm JWKS endpoint the resource server fetches signing keys from. */
    public static String jwksUri() {
        return issuerUri() + "/protocol/openid-connect/certs";
    }

    /**
     * Ensure a Keycloak account exists for {@code email} (idempotent), then mint an RS256 access token for it via
     * the direct-access grant. This is the token a secured-profile test attaches as its {@code Bearer}.
     */
    public static String tokenFor(String email, String password) {
        ensureUser(email, password);
        return passwordGrant(email, password);
    }

    /** Admin-create a realm user (username = email) with the given password if one does not already exist. */
    public static synchronized void ensureUser(String email, String password) {
        ensureUser(email, password, true);
    }

    /**
     * As {@link #ensureUser(String, String)}, but with an explicit {@code emailVerified} flag — an unverified
     * email exercises {@code OwnerResolver}'s rule that claim-by-email requires a verified address.
     */
    public static synchronized void ensureUser(String email, String password, boolean emailVerified) {
        final String adminToken = adminToken();
        final JsonNode existing = getJson(
                baseUrl() + "/admin/realms/" + REALM + "/users?exact=true&email="
                        + URLEncoder.encode(email, StandardCharsets.UTF_8),
                adminToken);
        if (existing.isArray() && !existing.isEmpty()) {
            return;
        }
        // requiredActions=[] and a complete profile (first/last name) keep the account "fully set up" — otherwise
        // an admin-created user can inherit realm default actions (verify-profile/email) that block the password grant.
        final String body = "{"
                + "\"username\":" + jsonString(email) + ","
                + "\"email\":" + jsonString(email) + ","
                + "\"firstName\":\"Test\","
                + "\"lastName\":\"User\","
                + "\"emailVerified\":" + emailVerified + ","
                + "\"enabled\":true,"
                + "\"requiredActions\":[],"
                + "\"credentials\":[{\"type\":\"password\",\"value\":" + jsonString(password) + ",\"temporary\":false}]"
                + "}";
        createUser(email, body);
    }

    /**
     * Mint a token that carries {@code sub} and the {@code /mcp} audience but NO {@code email} claim, via the
     * client-credentials grant of an admin-registered service-account client — the real-world shape of a valid
     * token no user account can be resolved or provisioned for.
     */
    public static synchronized String serviceAccountToken(String clientId, String clientSecret) {
        ensureServiceAccountClient(clientId, clientSecret);
        final JsonNode token = postForm(baseUrl() + "/realms/" + REALM + "/protocol/openid-connect/token", Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "grant_type", "client_credentials"));
        final JsonNode accessToken = token.get("access_token");
        if (accessToken == null) {
            throw new IllegalStateException("No access_token for service account '" + clientId + "': " + token);
        }
        return accessToken.asText();
    }

    /** Admin-register a confidential service-account client whose default scopes carry the /mcp audience. */
    private static void ensureServiceAccountClient(String clientId, String clientSecret) {
        final String body = "{"
                + "\"clientId\":" + jsonString(clientId) + ","
                + "\"secret\":" + jsonString(clientSecret) + ","
                + "\"publicClient\":false,"
                + "\"serviceAccountsEnabled\":true,"
                + "\"standardFlowEnabled\":false,"
                + "\"directAccessGrantsEnabled\":false,"
                + "\"defaultClientScopes\":[\"basic\",\"pensieve:read\"]"
                + "}";
        final HttpResponse<String> response = send(HttpRequest.newBuilder(
                        URI.create(baseUrl() + "/admin/realms/" + REALM + "/clients"))
                .header("Authorization", "Bearer " + adminToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
        // 201 = created; 409 = already registered by an earlier test (both are fine).
        if (response.statusCode() != 201 && response.statusCode() != 409) {
            throw new IllegalStateException("Failed to create service-account client '" + clientId + "': "
                    + response.statusCode() + " " + response.body());
        }
    }

    /** Admin-change a realm user's email (marked verified), simulating an email change made at the IdP. */
    public static synchronized void updateUserEmail(String currentEmail, String newEmail) {
        final String adminToken = adminToken();
        final JsonNode existing = getJson(
                baseUrl() + "/admin/realms/" + REALM + "/users?exact=true&email="
                        + URLEncoder.encode(currentEmail, StandardCharsets.UTF_8),
                adminToken);
        if (!existing.isArray() || existing.isEmpty()) {
            throw new IllegalStateException("No Keycloak user with email '" + currentEmail + "' to update");
        }
        final String id = existing.get(0).get("id").asText();
        final String body = "{\"email\":" + jsonString(newEmail) + ",\"emailVerified\":true}";
        final HttpResponse<String> response = send(HttpRequest.newBuilder(
                        URI.create(baseUrl() + "/admin/realms/" + REALM + "/users/" + id))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build());
        if (response.statusCode() != 204) {
            throw new IllegalStateException("Failed to update Keycloak user email '" + currentEmail + "': "
                    + response.statusCode() + " " + response.body());
        }
    }

    private static void createUser(String label, String body) {
        final HttpResponse<String> response = send(HttpRequest.newBuilder(
                        URI.create(baseUrl() + "/admin/realms/" + REALM + "/users"))
                .header("Authorization", "Bearer " + adminToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
        // 201 = created; 409 = a concurrent test already created it (both are fine).
        if (response.statusCode() != 201 && response.statusCode() != 409) {
            throw new IllegalStateException("Failed to create Keycloak user '" + label + "': "
                    + response.statusCode() + " " + response.body());
        }
    }

    /** Mint an access token for an existing realm user via the client's direct-access (password) grant. */
    public static String passwordGrant(String email, String password) {
        // scope=openid only: pensieve:read + email are default client scopes and attach automatically (requesting
        // a default scope by name is rejected by Keycloak as invalid_scope).
        final JsonNode token = postForm(baseUrl() + "/realms/" + REALM + "/protocol/openid-connect/token", Map.of(
                "client_id", CLIENT_ID,
                "grant_type", "password",
                "username", email,
                "password", password,
                "scope", "openid"));
        final JsonNode accessToken = token.get("access_token");
        if (accessToken == null) {
            throw new IllegalStateException("No access_token for '" + email + "': " + token);
        }
        return accessToken.asText();
    }

    private static String adminToken() {
        final JsonNode token = postForm(baseUrl() + "/realms/master/protocol/openid-connect/token", Map.of(
                "client_id", "admin-cli",
                "grant_type", "password",
                "username", ADMIN_USER,
                "password", ADMIN_PASS));
        return token.get("access_token").asText();
    }

    private static JsonNode postForm(String url, Map<String, String> form) {
        final StringBuilder encoded = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (encoded.length() > 0) {
                encoded.append('&');
            }
            encoded.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        final HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encoded.toString()))
                .build());
        return parse(response.body());
    }

    private static JsonNode getJson(String url, String bearer) {
        final HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + bearer)
                .GET()
                .build());
        return parse(response.body());
    }

    private static HttpResponse<String> send(HttpRequest request) {
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException("Keycloak request failed: " + request.uri(), e);
        }
    }

    private static JsonNode parse(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("Unparseable Keycloak response: " + body, e);
        }
    }

    private static String jsonString(String value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unencodable value", e);
        }
    }
}
