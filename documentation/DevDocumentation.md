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

The resource-server settings live in `application-secured.properties` (`pensieve.oauth2.issuer`, `pensieve.oauth2.jwk-set-uri`, `pensieve.oauth2.audience`, each env-overridable as `PENSIEVE_OAUTH2_*`). `entitlement.trial-days` (env `ENTITLEMENT_TRIAL_DAYS`, default 30) is global.

## Authentication (Keycloak and OAuth 2.1)

**Keycloak is the single authorization server for both the web app and MCP**, and the API is a pure **OAuth 2.0 resource server** — it mints no tokens, stores no passwords, and has no login, registration, or refresh endpoints. For more information on the auth check the keycloak/README.md

## Deployment (production topology)

Production is defined by `dockerCompose/compose.production.yaml`, `Caddyfile`, and `dockerCompose/.env.production.example` (copy to `.env` in that same directory and fill in). The `.env` lives beside the compose file so compose auto-loads it — `docker compose -f dockerCompose/compose.production.yaml up -d` needs no `--env-file`. A *missing* `.env` only warns, per variable, and then the stack dies on boot, so the deploy script asserts it exists. The file pins `name: pensieve` as its compose project, so the project no longer depends on the checkout directory's name. **Caddy is the only public service** — it terminates TLS and binds ports 80/443, reverse-proxying three hostnames to private services: the app (`frontend`), the MCP sidecar (`mcp`), and auth (`keycloak`). Everything else — `backend`, `db`, `keycloak`, `keycloak-db`, `mcp`, and `frontend` — is private with no published ports and is reachable only over the docker-compose network.

**Production runs secured.** The backend is started with `SPRING_PROFILES_ACTIVE: docker,secured` and its `PENSIEVE_OAUTH2_*` env, the sidecar with `MCP_AUTH_MODE=required`, and Keycloak has its own Postgres (`keycloak-db`), separate from the app database. There is **no `flyway` service** here — the production backend runs Flyway on startup — and the app database keeps a named volume. It matches `dockerCompose/compose.secured.yaml` in posture, and is the opposite of `dockerCompose/compose.unsecured.yaml` (see [Security Mode in Docker](#security-mode-in-docker)).

Production Keycloak imports its **own realm file** — `keycloak/import-prod/pensieve-realm.json`, not the dev one. The prod realm ships with the dev-only surface removed: **no test users**, **no `pensieve-test-client`** (no public client, no direct-access grants), **no anonymous DCR** (remote MCP hosts are pre-registered via the admin console), and `sslRequired=external`. Its deployment-specific values — the `pensieve:read` Audience mapper's `https://<MCP_DOMAIN>/mcp` audience, the `pensieve-web` redirect URIs/origins, and the web client secret — are `${PENSIEVE_*}` placeholders that Keycloak resolves from the service environment at import time (wired from `.env` in `dockerCompose/compose.production.yaml`), so there is **no manual pre-deploy realm edit**. The import runs once, on first boot with an empty `keycloak-db`. After the first deployment, decode an access token and verify `aud` and `iss`: the audience is validated by **both** the MCP sidecar and the backend resource server, so a mismatch (including a literal unsubstituted `${PENSIEVE_...}`) rejects every request.

### Accounts and providers

**Domain**

- Registrar is `Porkbun`
- DNS host is `Porkbun (registrar-provided DNS)`
- Apex domain is `sethcondie.com`
- Registered on `2026-08-13`

**Hostnames.** 

- APP_DOMAIN=pensieve.sethcondie.com
- MCP_DOMAIN=mcp.pensieve.sethcondie.com
- AUTH_DOMAIN=auth.pensieve.sethcondie.com

**Email relay — Resend** 

- Account email is `8bitdad7dc@gmail.com`
- Verified domain is `pensieve.sethcondie.com`
- Subdomain is verified and will be used, over the apex domain, because if a reputation issue comes up with, the subdomain it can be changed in the future. (With some manual syncing with the keycloak realm and this project.)
- Verified on `2026-08-13`
- SMTP_FROM is `no-reply@pensieve.sethcondie.com`
- Replies to that address will be discarded
- DNS records published (DKIM `TXT`, SPF, `MX` for bounces, DMARC) on `2026-08-13`

`SMTP_FROM` is effectively frozen. Keycloak resolves it into the realm at first import and never reads
`.env` for it again, so changing it later means editing the live realm by hand — at which point `.env`
and the realm disagree. Pick the address users should see, then verify whichever domain permits it.

Public SMTP settings — confirmed against the Resend dashboard 2026-08-13:

- SMTP_HOST=smtp.resend.com
- SMTP_PORT=465
- SMTP_USER=resend (literally the string `resend`)
- SMTP_STARTTLS=false
- SMTP_SSL=true
- SMTP_PASSWORD is the Resend API key, sending-access only — password manager only, never here and
  never in git. Unlike `SMTP_FROM`, this one is cheap to rotate: revoke, regenerate, update `.env`,
  restart Keycloak.

Port and TLS flags are a **pair**, never mixed — `465 → STARTTLS=false, SSL=true` (implicit TLS, what
we use, and what the Resend dashboard hands you); `587 → STARTTLS=true, SSL=false`. Resend listens on
25, 465, 587, 2465, and 2587 at the same time, so the port is purely a client-side choice with nothing
to configure on their end. Mixing the pair fails at connect time with a TLS handshake error that does
not name the port as the cause.

Note for provisioning: DigitalOcean blocks outbound port 25 by default and may restrict others pending
review, so the Droplet's day-one connectivity test is against **465** — `nc -vz smtp.resend.com 465`.

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

The single-user seed endpoints (`/v1/function/seedSampleData`, `/seedMyCollection`) only populate one owner. To exercise every role (GUEST, TRIAL, PAID, LAPSED, ADMIN) and the showcase-switching features against realistic multi-user data, there is a **multirole seed set** with **two consumers** that must never share a database:

1. **Integration tests** — `SeededUsersFixture` seeds through MockMvc inside the test JVM; `SeededDataMatrixTests` asserts the capability/showcase matrix against it.
2. **Live environments (dev/staging)** — `scripts/seed-test-data.sh` runs the same choreography over real HTTP for manual testing, front-end work, and smoke checks.

Both load the same eight seed files from `src/main/resources/seeders/` and perform the same choreography, so they never drift. Never point the script at the integration-test database (the suite seeds itself), and never make a test depend on an externally pre-seeded database.

## Where to Find the Requirements

- **Design intent** lives in the Javadoc-style comments on the `Entity` and `System` classes (and the `Keychain`).
- **Per-entity requirements** live in that entity's integration test. For example, the rules for a video game box are documented and enforced in `VideoGameBoxTests.java`. When in doubt about expected behavior, read the test.
- **The HTTP contract** is in [`openapi.yaml`](./openapi.yaml); ready-to-run example requests are in [`api.postman_collection.json`](./api.postman_collection.json).
- **The MCP sidecar** documents its tools, env vars, and host setup in [its own repository's README](https://github.com/sethhaskellcondie/the-game-pensieve-mcp#readme).
- **Keycloak** (realm contents, clients, scopes, minting a token by hand) is in [`../keycloak/README.md`](../keycloak/README.md).
- **Notable past issues** are recorded in [`PastIssues.md`](./PastIssues.md).
