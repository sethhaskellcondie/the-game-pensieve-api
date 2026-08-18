# Developer Documentation

This document describes the design of the Game Pensieve API and the conventions a developer should understand before working on it. For setup and run instructions, see the [README](../README.md). For the full HTTP contract, see [`openapi.yaml`](./openapi.yaml).

## Architecture Overview

The system is a CRUD-based entity service organized into four horizontal layers. Every entity follows the same naming convention, illustrated here with the `System` entity:

| Layer | Naming convention | Example | Responsibility |
| --- | --- | --- | --- |
| Controller | `(Entity)Controller.java` | `SystemController.java` | HTTP endpoints, request/response serialization |
| Gateway | `(Entity)Gateway.java` | `SystemGateway.java` | The only public entry point into the domain |
| Service | `(Entity)Service.java` | `SystemService.java` | Business logic and validation |
| Repository | `(Entity)Repository.java` | `SystemRepository.java` | Persistence via JDBC Template |

**A layer may only call layers at its own level or below it.** Controllers call gateways, gateways call services, services call repositories. Nothing calls upward.

The packages reflect this split:

- `api/` — the application layer: controllers, the controller advice (`ApiControllerAdvice`), the standard response wrapper (`FormattedResponseBody`), MVC configuration, the OAuth2 resource-server setup (`api/security/`), and the per-request tenant boundary (`api/tenant/`).
- `domain/` — everything else, organized by concern (`entity/`, `customfield/`, `filter/`, `backupimport/`, `metadata/`, `counts/`, `auth/`, `exceptions/`).

Two things live outside this Java tree and are documented in their own sections below: the **MCP sidecar** (a separate TypeScript process in [its own repository](https://github.com/sethhaskellcondie/the-game-pensieve-mcp) — see [MCP Sidecar](#mcp-sidecar)) and **Keycloak** (`keycloak/`, the authorization server — see [Authentication](#authentication-keycloak-and-oauth-21)).

## Domain Encapsulation

The domain is the core of the system. It is designed so that it could be compiled on its own or transplanted into another system. The **only** way to reach the domain is through the gateways, and the only types the domain exports are:

- Gateway classes
- Data Transfer Objects (DTOs)
- Exceptions
- The [`Keychain`](#the-keychain)

Everything else in the domain is internal and must not leak into the API layer.

### The Keychain

`Keychain.java` is the master list of every entity in the system. Each entity has a string key (singular, initial camel case — e.g. `videoGame`, `videoGameBox`). Keys drive cross-cutting features such as filters and custom fields, which is why they are centralized rather than hard-coded per entity.

When you add a new entity, you must:

1. Add its key constant to the `Keychain` and include it in `getAllKeys()`.
2. Map its key to its primary table alias in `getTableAliasByKey()` — this alias is what the filter system uses when it builds SQL, and it must match the alias used in that entity repository's base query.

## Configuration and Profiles

Global settings live in `application.properties` and apply to every profile. Profile-specific settings live in `application-<profile>.properties`. Notable global settings: the HikariCP connection pool, Flyway migration location (`classpath:migrations`), and **virtual threads (Project Loom) are enabled** (`spring.threads.virtual.enabled=true`) to improve throughput for I/O-bound request handling.

| Profile | Purpose | Datasource |
| --- | --- | --- |
| `local` (default) | Running against a local Postgres | `jdbc:postgresql://localhost:5432/pensieve-db` |
| `docker` | Running inside the compose network | `jdbc:postgresql://db:5432/pensieve-db` |
| `secured` | **Overlay, not a datasource profile** — turns on authentication and the role/capability gates | — (combined, e.g. `docker,secured`) |
| `test-container` | Integration tests | Testcontainers (`jdbc:tc:postgresql:...`) |
| `import-tests` | Backup/import tests | Testcontainers |
| `rls-tests` | Tenancy / RLS repository tests | Testcontainers |
| `seeded-tests` | The multi-role seed matrix suite | Testcontainers |
| `filter-tests1`–`filter-tests8` | Filter integration tests, split across profiles | Testcontainers |
| `repository-tests` | Entity repository tests (the `afterLoad()` hydration hook) | Testcontainers |

The default credentials in local/docker are user `postgres`, password `root`. Override the active profile with `spring.profiles.active`.

`secured` is an **overlay** profile: it is always activated alongside a datasource profile (`docker,secured` in `dockerCompose/compose.secured.yaml` and in production, `{"test-container", "secured"}` in the secured test suites) and it is the single switch between the two builds of the app:

- **default (permit-all)** — no authentication; every request is anonymous and resolves to the default showcase owner, and `AccessService` reports full access. This preserves the original single-user behavior.
- **`secured`** — the app is an OAuth2 resource server and the capability matrix is enforced (see [Authentication](#authentication-keycloak-and-oauth-21) and [Roles and Capabilities](#roles-and-capabilities)).

Row-Level Security is **not** gated by the profile — it runs identically in both builds; only the resolved owner id differs. `GET /v1/heartbeat` reports which build is running (`{"message": "thump thump", "secureMode": true|false}`), which is how the front end and the MCP sidecar discover the server's posture without a token.

### The production guard: `secured` is enforced, not merely expected

Because the default build is permit-all, **losing the `secured` profile is a silent failure.** A bad template, an env file that did not load, or a typo in `SPRING_PROFILES_ACTIVE` produces an app that starts cleanly against the real production datasource, logs nothing unusual, answers the heartbeat, and serves every endpoint to anonymous callers. Nothing about it announces that authentication is gone. `secureMode` in the heartbeat would report `false`, but only if someone happened to look.

So production declares itself with a marker: **`PENSIEVE_ENV=production`**, set on the `backend` service in `compose.production.yaml`. `api/security/ProductionSecurityGuard` reads it during context refresh and **aborts startup** if the marker is present while `secured` is not active. The container exits rather than serving.

Two properties of this design matter, and both are easy to undo by accident:

- **The marker is a separate variable from the profile list, on purpose.** A guard keyed on `SPRING_PROFILES_ACTIVE` would be silenced by exactly the mistake it exists to catch — a line that lost `secured` has probably lost `docker` too, and an empty profile list would disarm the check entirely. Do not "simplify" it to read the profiles.
- **The check is one-directional.** Running `secured` *without* the marker is normal and supported (the dev secured stack, the Testcontainers suites). Only marker-without-`secured` is fatal, and a marker value other than `production` (case-insensitive) does not arm it at all.

This guard is invisible until it fires, and when it fires it aborts a deploy. The exception message names the missing profile and lists the active ones; if you meet it, the fix is the `SPRING_PROFILES_ACTIVE` line, not the guard.

The resource-server settings live in `application-secured.properties` (`pensieve.oauth2.issuer`, `pensieve.oauth2.jwk-set-uri`, `pensieve.oauth2.audience`, each env-overridable as `PENSIEVE_OAUTH2_*`). `entitlement.trial-days` (env `ENTITLEMENT_TRIAL_DAYS`, default 30) is global.

## Authentication (Keycloak and OAuth 2.1)

**Keycloak is the single authorization server for both the web app and MCP**, and the API is a pure **OAuth 2.0 resource server** — it mints no tokens, stores no passwords, and has no login, registration, or refresh endpoints. For more information on the auth check the keycloak/README.md

## Deployment (production topology)

This section covers the *topology* — the compose file, the Caddyfile, the edge posture, and how the
deploy scripts work. The production *host* (providers, Droplet provisioning, first bringup, backups)
is documented in [`buildFromScratch.md`](buildFromScratch.md).

Production is defined by `dockerCompose/compose.production.yaml`, `Caddyfile`, and `dockerCompose/.env.production.example` (copy to `.env` in that same directory and fill in). The `.env` lives beside the compose file so compose auto-loads it — `docker compose -f dockerCompose/compose.production.yaml up -d` needs no `--env-file`. A *missing* `.env` only warns, per variable, and then the stack dies on boot, so the deploy script asserts it exists. The file pins `name: pensieve` as its compose project, so the project no longer depends on the checkout directory's name. **Caddy is the only public service** — it terminates TLS and binds ports 80/443, reverse-proxying three hostnames to private services: the app (`frontend`), the MCP sidecar (`mcp`), and auth (`keycloak`). Everything else — `backend`, `db`, `keycloak`, `keycloak-db`, `mcp`, and `frontend` — is private with no published ports and is reachable only over the docker-compose network.

**Production runs secured.** The backend is started with `SPRING_PROFILES_ACTIVE: docker,secured` and its `PENSIEVE_OAUTH2_*` env, the sidecar with `MCP_AUTH_MODE=required`, and Keycloak has its own Postgres (`keycloak-db`), separate from the app database. There is **no `flyway` service** here — the production backend runs Flyway on startup — and the app database keeps a named volume. It matches `dockerCompose/compose.secured.yaml` in posture, and is the opposite of `dockerCompose/compose.unsecured.yaml` (see [Security Mode in Docker](#security-mode-in-docker)).

Production Keycloak imports its **own realm file** — `keycloak/import-prod/pensieve-realm.json`, not the dev one. The prod realm ships with the dev-only surface removed: **no test users**, **no `pensieve-test-client`** (no public client, no direct-access grants), **no anonymous DCR** (remote MCP hosts are pre-registered via the admin console), and `sslRequired=external`. Its deployment-specific values — the `pensieve:read` Audience mapper's `https://<MCP_DOMAIN>/mcp` audience, the `pensieve-web` redirect URIs/origins, and the web client secret — are `${PENSIEVE_*}` placeholders that Keycloak resolves from the service environment at import time (wired from `.env` in `dockerCompose/compose.production.yaml`), so there is **no manual pre-deploy realm edit**. The import runs once, on first boot with an empty `keycloak-db`. After the first deployment, decode an access token and verify `aud` and `iss`: the audience is validated by **both** the MCP sidecar and the backend resource server, so a mismatch (including a literal unsubstituted `${PENSIEVE_...}`) rejects every request.

**Prod-only realm hardening** (full rationale in `keycloak/README.md`): a stronger password policy
(`length(12)` plus mixed case, `notUsername`/`notEmail`, `passwordHistory(3)`) — not mirrored to dev, where
it would break the test suite's fixture accounts; **refresh-token rotation** (`revokeRefreshToken: true`,
`refreshTokenMaxReuse: 0`), which makes a second presentation of a spent refresh token a session-revoking
event, so the web BFF single-flights its silent refresh (`the-game-pensieve-web-v2/src/proxy.ts`) rather
than letting concurrent `/api/*` calls each spend the same token; a **pinned** post-logout redirect instead
of a wildcard; and **`pensieve:read` removed from the realm's default client scopes**. That last one is the
structural difference between the two realms and the one most likely to surprise: the `/mcp` audience is
attached by a mapper on that scope, so while it was a realm default, every client in the realm — including
`admin-cli`, which has direct-access grants — minted tokens the backend and sidecar accepted. `pensieve-web`
lists the scope explicitly and is unaffected; a **hand-registered MCP connector must be given it explicitly**
or its tokens carry no audience at all. The sidecar now also *requires* the scope rather than merely
advertising it, answering `403 insufficient_scope`.

**Startup ordering, healthchecks, and resource limits** are covered in
[`dockerComposeExplainer.md`](dockerComposeExplainer.md#healthchecks-and-startup-order): every service has a
healthcheck and `depends_on: condition: service_healthy`, and every service has a `mem_limit`/`cpus` ceiling
sized for the 4 GB box. Note that compose **reports** an unhealthy container but does not restart it —
`restart: unless-stopped` reacts only to a process exiting.

**Backend logs** live on the `backend_logs` volume at `/var/log/pensieve` (`PENSIEVE_LOG_PATH`). Without
that mount the log is written into the container's writable layer and destroyed on every redeploy. Read it
with `docker compose -f dockerCompose/compose.production.yaml exec backend cat /var/log/pensieve/spring.log`.
An unexpected 500 no longer echoes the exception message to the caller — it returns a fixed message plus a
random reference id, and the real exception is logged against that id. `ApiControllerAdvice` is reachable by
**anonymous** callers through the permit-all showcase read surface, and a Postgres `DataAccessException`
message carries the failing SQL, constraint names, and the internal host `db:5432`. When a user reports an
error, ask for the reference id and grep the log for it.

### The edge posture

Caddy is the security boundary as well as the TLS terminator, and it adds nothing by default — an
unconfigured Caddy site ships no HSTS, no `nosniff`, no framing policy. The `Caddyfile` therefore defines a
`(security_headers)` snippet and imports it into all three site blocks: **HSTS** (one year,
`includeSubDomains`, deliberately **without** `preload` — that submission is close to irreversible and
covers subdomains you may not control yet), **`X-Content-Type-Options: nosniff`**, **`Referrer-Policy:
strict-origin-when-cross-origin`**, and **`Content-Security-Policy: frame-ancestors 'none'`** with
`X-Frame-Options: DENY` behind it. Caddy's own `Server` banner is stripped.

`Referrer-Policy` is the one whose motivation is specific rather than generic: password-reset and
`execute-actions-email` links carry single-use tokens **in the URL**, and the policy is what keeps a full
reset URL out of the `Referer` header when the user clicks onward to a third-party site. The CSP is scoped
to framing only — a full CSP for a Next.js app needs `script-src` work that is not a launch blocker and
would break the app silently if guessed at.

**Request bodies are capped twice.** `POST /v1/function/import` binds an `@RequestBody Map` that Jackson
materializes fully into the heap before the controller runs, and Spring Boot has no setting that covers a
JSON body (`max-http-form-post-size` is form-encoded only, `spring.servlet.multipart.*` is multipart only).
On a 4 GB host with a 1 GB-capped JVM beside two databases and Keycloak, one oversized upload is an
out-of-memory kill that costs an attacker a single request. So:

- **At the edge**, Caddy's `request_body max_size` — 10 MB on the app host, 1 MB on the MCP and auth hosts.
  This enforces on bytes actually received, so it also covers a chunked request that declares no length.
- **In the app**, `RequestSizeLimitFilter` (`pensieve.max-request-body-bytes`, 10 MB) refuses on
  `Content-Length` with a 413 before anything is deserialized, and runs ahead of the security chain — there
  is no reason to authenticate a request that is going to be refused on size. It deliberately does not wrap
  the input stream to count bytes, because the limit would then trip inside Jackson's read and Spring would
  bury it in `HttpMessageNotReadableException`, turning a clean 413 into a misleading 500.

The two numbers are meant to move together: raise one and raise the other in the same release. 10 MB is
sized from real data — the largest collection backup in this repo is ~3.3 MB.

**Rate limiting is deliberately absent**, and this is the largest known gap at launch. Caddy's `rate_limit`
is a third-party plugin, so adding it means building and maintaining a custom Caddy image — a change to the
edge artifact itself. The partial mitigation in place is the production realm's brute-force protection,
which is **per-account, not per-IP**, so it does nothing about volumetric abuse of the anonymous read
surface or of Keycloak's token endpoint. It is the first item in the post-launch backlog.

**Do not widen the basic-auth matcher** on the auth host — it covers exactly the interactive admin
login surface (console shell, master login page, login-actions) and nothing else. `/realms/pensieve/*`
(authorize, token, JWKS, `.well-known`) and `/resources/*` (static assets for the console *and* the
public login pages) must stay open; gating the latter leaves real users at an unstyled login screen.
The Admin REST API must stay open — the console calls it with `Authorization: Bearer`, a request has
exactly one `Authorization` header, and a basic-auth gate there is therefore unsatisfiable: the
credential popup loops forever (found live 2026-08-17). The rest of `/realms/master/*` (token
endpoint, session iframes) must stay open too — the console session-refreshes through them in
background fetches where browsers don't replay basic credentials, so gating them causes mid-session
popups whose cancel logs the admin out (found live 2026-08-18). Keycloak enforces its own auth on all
of it; the compensating control for the open master token endpoint is brute-force detection enabled
on the master realm. `scripts/prod-rehearsal.sh` asserts the open/gated directions and will catch a
mistake here.

**A green rehearsal is the gate for the publish.** The order is rehearse → release, never the reverse:
`scripts/prod-rehearsal.sh` is the only thing that exercises `dockerCompose/compose.production.yaml` and the
`Caddyfile` as production will run them, and its first run found a login-blocking defect
(`PastIssues.md`, the BFF-origin entry) that every other gate was structurally blind to. Published image
tags are immutable, so a defect the rehearsal would have caught costs a whole version number once it ships.
Run it after any change to the production topology — compose file, Caddyfile, realm, or the images — and
run the SMTP half (`SMTP_TEST_TO=<real inbox>`) at least once against the real relay before a release that
touches the realm's email settings, because those are baked at import.

**The version intended for first production boot is `1.0.0`** — released 2026-08-14, the first version ever
published (there is no earlier tag). The Droplet's first deploy checks out `v1.0.0` explicitly, not
"whatever is newest": that tag is the one whose images passed the full release gate and whose compose pins,
`Caddyfile`, and realm import were rehearsed together. If a later version exists by then because something
needed fixing, this line is what gets updated — the deploy still names its version deliberately.

**Deploying and rolling back** *(verified live 2026-08-17: dry run, real `1.0.1` deploy in under two
minutes, deliberate rollback to `1.0.0`, redeploy of `1.0.1`)*: `make deploy VERSION=X.Y.Z` runs
`scripts/deploy-production.sh` — local preflight that fails in seconds (version shape, `latest` rejected,
tag on origin, the tag carries the remote deploy script — `v1.0.0` predates the pipeline and is
hand-deploy-only, all three images on Docker Hub with `linux/amd64`, host reachable), then one SSH call that
checks out `v$VERSION` on the Droplet and runs `scripts/deploy-production-remote.sh`: assert → record the
running version → verify pins → `pg_dump` both databases → pull → `up -d` → wait for the public URLs and
assert the running containers are `:$VERSION` → prune → append to the deploy log. **Rollback is the same
command with the previous version tag** — no separate rollback script, deliberately; the deploy prints the
exact rollback command on failure and after success. Migrations are forward-only (kept additive), so an
image rollback never rolls back the schema. Rehearse with `DRY_RUN=yes` — every check runs, nothing
changes, including on the Droplet (it fetches tags but does not move the checkout). Both halves are
documented in full in `scriptExplainer.md`.

### Bootstrap: claim the seeded default showcase row

A fresh database has exactly one user: migration `V1_13` seeds a row with
`email = 'showcase@internal.local'` and `is_public_showcase = TRUE` (a partial unique index guarantees there
is only ever one), and every anonymous request resolves to it — it is the default showcase whose data guests
see. `V1_19` added `users.keycloak_sub` (nullable, UNIQUE); NULL means "not yet linked to a Keycloak
account". The bootstrap makes this seeded row *yours*: your email, the ADMIN pin, and — on your first
login — your Keycloak `sub` stamped onto it.

The mechanism is `OwnerResolver.resolveOrProvision`, which resolves every authenticated request in this
order:

1. **By `sub`** — if a row is linked to the token's `sub`, that row wins, unconditionally.
2. **Claim by email** — no row by `sub`, and the token's `email_verified` claim is `true`: an existing row
   with the token's email (trimmed, lowercased) is claimed by stamping the `sub` onto it. The verified-email
   requirement is a takeover guard — without it, anyone who registered your address at the IdP could claim
   your row.
3. **JIT trial** — otherwise a fresh 30-day TRIAL row is inserted for this identity. If the insert hits the
   email UNIQUE constraint (a row with that address exists but step 2 did not apply — almost always an
   unverified email), the request is refused with a 403 naming the conflict.

**⚠️ Step order is load-bearing: run the SQL *before* the first login.** Log in first and step 3 JIT-creates
a TRIAL row linked to your `sub`; from then on step 1 always wins and the showcase row can never be claimed.
Recovery: `DELETE FROM users WHERE keycloak_sub = '<your sub>' AND NOT is_public_showcase;` (safe only while
the mistaken row owns no data), then redo the procedure.

The procedure, shown with the production compose file (any secured stack works the same):

1. **Create your account in Keycloak.** Admin console (in production: `https://<AUTH_DOMAIN>/admin`, through
   the Caddy basic-auth gate once, then the Keycloak admin login — two credential pairs, two prompts,
   nothing more) → switch realm **master → pensieve** → Users →
   Create user: set username and email, and flip **Email verified → On** — by hand, every time. The realm
   ships with `verifyEmail` off by design (accounts are admin-created, there is no open registration), so
   nothing flips it for you; an unverified account logs in fine but skips the claim path and gets the 403
   email-conflict from step 3 above. Then Credentials → Set password with **Temporary off**.
2. **Point the seeded row at your email and pin ADMIN:**

   ```bash
   docker compose -f dockerCompose/compose.production.yaml exec db \
     psql -U postgres -d pensieve-db \
     -c "UPDATE users SET email = 'you@domain.com', role_override = 'ADMIN' WHERE is_public_showcase;"
   ```

   The email **must be lowercase** — Keycloak stores emails lowercased and the resolver compares the
   normalized form, so a mixed-case address here never matches. `role_override = 'ADMIN'` outranks the
   billing-derived role outright, and `uq_users_single_admin` allows exactly one pinned admin — this UPDATE
   fails if another row is already pinned.
3. **Log in once** through the app. The first authenticated API call claims the row by verified email and
   stamps `keycloak_sub`; there is nothing else to trigger.
4. **Verify the claim took**, from both sides:

   ```bash
   docker compose -f dockerCompose/compose.production.yaml exec db \
     psql -U postgres -d pensieve-db \
     -c "SELECT id, email, role_override, keycloak_sub IS NOT NULL AS linked FROM users WHERE is_public_showcase;"
   ```

   `linked` must be `t`, and `GET /api/auth/session` in the logged-in browser (which calls
   `GET /v1/auth/me` behind the scenes) must report role `ADMIN` — `unknown` means the backend rejected the
   token, not that the claim failed.

After the claim, harden per the checklist in [`buildFromScratch.md`](buildFromScratch.md): enable OTP on the
account, create a permanent Keycloak admin and delete the bootstrap one, and blank
`KC_ADMIN_USER`/`KC_ADMIN_PASSWORD` in `.env` (`KC_BOOTSTRAP_ADMIN_*` only ever applies to a first boot on
an empty `keycloak-db`).

### Accounts and providers

Moved to [`buildFromScratch.md`](buildFromScratch.md), the operations runbook for the production
host. Registrar, DNS, hostnames, the Resend relay and SMTP settings, and the Droplet's provisioning
record all live there — this document keeps the production *topology*; the host it runs on and the
providers around it are buildFromScratch territory.



## Testing Strategy

The project uses a **diamond testing strategy**: a broad layer of integration tests that exercise the stack from the controller down, plus focused unit tests for the parts that need more rigor (notably custom fields and filters).

- Integration tests use **MockMvc** (bundled with Spring Boot) to drive the controllers.
- They run against **Testcontainers** so each run gets an isolated Postgres instance with no cross-contamination between tests. **Docker must be running** for these tests.
- The filter integration tests are split across the `filter-tests1`–`filter-tests8` profiles to spread the container load.

### The secured-profile suites

Tests that exercise authentication (`*SecuredProfileTests`, `MultiTenancyTests`, `SeededDataMatrixTests`) run against a **real Keycloak Testcontainer** rather than mocked tokens — they mint genuine RS256 access tokens, and the app validates them exactly as it would in production:

- `KeycloakTestSupport` owns the container: a JVM-wide singleton started on first use and reused for the whole run (Ryuk reaps it). It mounts **the same realm file the docker-compose stack uses** (`keycloak/import/pensieve-realm.json`, by host path — no duplicated copy to drift), so its tokens carry the real audience, the `pensieve:read` scope, and `sub`/`email`. Tokens are minted through the realm's public `pensieve-test-client` with the direct-access (password) grant, and `ensureUser` admin-creates accounts on demand so tests keep their familiar "make a user, get a token" shape.
- `SecuredProfileTest` is the mix-in base that points `pensieve.oauth2.issuer`/`jwk-set-uri` at that container via `@DynamicPropertySource`. Subclasses keep their own `@SpringBootTest`/`@ActiveProfiles` because the datasource profile differs (`test-container` vs `seeded-tests`); the audience stays the fixed value from `application-secured.properties`.

## Seeding Multi-Role Test Data

The single-user seed endpoints (`/v1/function/seedSampleData`, `/seedMyCollection`) only populate one owner, and under `secured` they require the **SEED** capability, which only ADMIN holds. To exercise every role (GUEST, TRIAL, PAID, LAPSED, ADMIN) and the showcase-switching features against realistic multi-user data, there is a **multirole seed set** with **two consumers** that must never share a database:

1. **Integration tests** — `SeededUsersFixture` seeds through MockMvc inside the test JVM; `SeededDataMatrixTests` asserts the capability/showcase matrix against it.
2. **Live environments (dev/staging)** — `scripts/seed-test-data.sh` runs the same choreography over real HTTP for manual testing, front-end work, and smoke checks.

Both load the same eight seed files from `src/main/resources/seeders/` and perform the same choreography, so they never drift. Never point the script at the integration-test database (the suite seeds itself), and never make a test depend on an externally pre-seeded database.

**Neither consumer calls a seed endpoint** — both `POST /v1/function/import` with the payload in the request body, including for the default showcase (which reads `sampleData.json` from this repo). That is not an arbitrary choice: seeding requires the ADMIN-only SEED capability, impersonation (`X-Act-As-Owner`) adopts the **target's** role rather than the admin's, and `uq_users_single_admin` allows exactly one pinned admin — so an admin can never reach a seed endpoint on another account's behalf. Importing is what the temporary `PAID` pin in the choreography authorizes, and it removes any dependency on the server's working directory.

## Where to Find the Requirements

- **Design intent** lives in the Javadoc-style comments on the `Entity` and `System` classes (and the `Keychain`).
- **Per-entity requirements** live in that entity's integration test. For example, the rules for a video game box are documented and enforced in `VideoGameBoxTests.java`. When in doubt about expected behavior, read the test.
- **The HTTP contract** is in [`openapi.yaml`](./openapi.yaml); ready-to-run example requests are in [`api.postman_collection.json`](./api.postman_collection.json).
- **The MCP sidecar** documents its tools, env vars, and host setup in [its own repository's README](https://github.com/sethhaskellcondie/the-game-pensieve-mcp#readme).
- **Keycloak** (realm contents, clients, scopes, minting a token by hand) is in [`../keycloak/README.md`](../keycloak/README.md).
- **Notable past issues** are recorded in [`PastIssues.md`](./PastIssues.md).
