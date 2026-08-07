# Developer Documentation

This document describes the design of the Game Pensieve API and the conventions a developer should understand before working on it. For setup and run instructions, see the [README](../README.md). For the full HTTP contract, see [`openapi.yaml`](./openapi.yaml).

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Domain Encapsulation](#domain-encapsulation)
- [The Entity Pattern](#the-entity-pattern)
- [Entities and Relationships](#entities-and-relationships)
- [Custom Fields](#custom-fields)
- [The Filter System](#the-filter-system)
- [Backup and Import](#backup-and-import)
- [Metadata](#metadata)
- [Response Body Format](#response-body-format)
- [Configuration and Profiles](#configuration-and-profiles)
- [Authentication (Keycloak and OAuth 2.1)](#authentication-keycloak-and-oauth-21)
- [Multi-Tenancy and Row-Level Security](#multi-tenancy-and-row-level-security)
- [Roles and Capabilities](#roles-and-capabilities)
- [Public Showcases](#public-showcases)
- [MCP Sidecar](#mcp-sidecar)
- [Database and Migrations](#database-and-migrations)
- [Deployment (production topology)](#deployment-production-topology)
- [Testing Strategy](#testing-strategy)
- [Seeding Multi-Role Test Data](#seeding-multi-role-test-data)
- [Where to Find the Requirements](#where-to-find-the-requirements)
- [Docker Runtime Flow](#docker-runtime-flow)
- [Multiplatform Deployment](#multiplatform-deployment)

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

- `api/` — the web layer: controllers, the controller advice (`ApiControllerAdvice`), the standard response wrapper (`FormattedResponseBody`), MVC configuration, the OAuth2 resource-server setup (`api/security/`), and the per-request tenant boundary (`api/tenant/`).
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

## The Entity Pattern

Every entity implements the generic `Entity<RequestDto, ResponseDto>` interface and carries:

- An `Integer id` (`null` until persisted — see `isPersisted()`).
- A list of `CustomFieldValue`s.
- Conversions to and from its request and response DTOs.

Shared behavior is provided by the abstract base classes `EntityServiceAbstract`, `EntityRepositoryAbstract`, and `EntityGatewayAbstract`. Concrete entities extend these and supply only what is specific to them.

A few conventions that are worth knowing:

- **`createNew()` is implemented per service, not in the base class.** Java cannot call `new T()` on a generic type, so each service constructs its own concrete instance. `updateExisting()` and `deleteById()` are shared in `EntityServiceAbstract`.
- **Services hold their concrete repository type.** `EntityServiceAbstract` takes the repository as a generic parameter (`R extends EntityRepository<...>`), so a service calls subtype-specific repository methods (like `getIdByName()`) directly — no downcasting.
- **Related-data hydration goes through `afterLoad()`.** Every query path in `EntityRepositoryAbstract` — `getWithFilters()`, `getById()`, `getByIds()`, and all of the deleted/include-deleted variants — funnels loaded entities through the single `afterLoad(List<T>)` hook after custom field values are set. A repository that needs related data on its entities (like the video game ↔ box join table ids) overrides that one hook instead of decorating each query method, so no path can be missed. Covered by `domain/entity/EntityRepositoryAfterLoadTests`.
- **POSTs are idempotent by intent.** Services run a duplication check inside `createNew()` so that repeating a create request returns a `400` rather than silently inserting a duplicate.
- **Soft deletes.** Tables carry a `deleted_at` column; entities expose `isDeleted()`. Deletion sets the timestamp rather than removing the row.
- **Request vs. Response vs. Slim DTOs.** `(Entity)RequestDto` is the input shape; `(Entity)ResponseDto` is the output shape. Several entities also have a `Slim(Entity)` form — a lightweight projection used when an entity is embedded inside another's response (for example, the systems and games nested inside a video game box).
- **Not every entity exposes every CRUD operation through its controller.** All entities implement the full set internally, but a controller may omit endpoints it does not need.

## Entities and Relationships

This program tracks a game collection. The current entities are:

- **System** — a gaming platform (e.g. a console). Referenced by games and boxes.
- **Toy** — a collectible toy.
- **VideoGame** — an individual video game title, optionally tied to a `System`.
- **VideoGameBox** — a physical or collection package that contains one or more video games. Flagged `is_physical` and `is_collection`. Linked to video games through the `video_game_to_video_game_box` join table (a box may hold many games).
- **BoardGame** — a board game title.
- **BoardGameBox** — a physical package associated with a board game.

The video game and video game box relationship is the most involved: boxes and games each reference a `System`, and the join table connects them many-to-many.

## Custom Fields

Custom fields let users attach their own metadata to any entity without schema changes. A custom field is defined once (name, type, and which entity key it applies to), and its values are stored separately from the core entity rows. The supported value types mirror the filter types: text, number, boolean, and time. Custom fields may also define a fixed set of selectable options (`CustomFieldOption`).

Because custom field values are typed, they participate fully in the filter system (see below).

## The Filter System

All search endpoints use an RPC-style call: `POST /{resource}/function/search` with a filter array in the request body. When the array is empty, all resources are returned.

### Filter Types

| Type | Operators |
| --- | --- |
| **Text** | `equals`, `not_equals`, `contains`, `starts_with`, `ends_with` |
| **Number** | `equals`, `not_equals`, `greater_than`, `greater_than_equal_to`, `less_than`, `less_than_equal_to` |
| **Boolean** | `equals` |
| **Time** | `since`, `before` |
| **System** | `equals`, `not_equals` (video games and video game boxes only) |
| **Sort** | `order_by`, `order_by_desc` |
| **Pagination** | `limit`, `offset` |

### Filtering on a Game's System

The `system` filter type exists specifically to filter video games and video game boxes by their associated system. Although a system reference is numeric, it gets its own filter type rather than reusing `number` because:

- It only permits `equals` and `not_equals`.
- It communicates intent — the field represents a relationship, not an arbitrary number.
- It blocks nonsensical operations such as `greater_than` on a system id.
- It returns clear validation errors for invalid operators.

**Available fields:** `system_id` on both video games and video game boxes.

**Example request:**

```json
{
  "filters": [
    {
      "entityKey": "videoGame",
      "type": "system",
      "field": "system_id",
      "operator": "equals",
      "operand": "1"
    }
  ]
}
```

### Custom Field Filters

Custom fields are filterable using the filter type that matches the field's data type. When custom field filters are present, the filter-to-SQL translation gives each one an indexed alias pair (`fields1`/`values1`, `fields2`/`values2`, and so on) so multiple custom field filters can be combined in a single query. The table alias an entity contributes to these queries comes from `Keychain.getTableAliasByKey()`.

## Backup and Import

The `backupimport` package supports exporting the entire collection to a single JSON document and importing it back. Import is idempotent, but with an important caveat: existence checks must resolve parent entities by their natural keys (name/title), **not** by the ids stored in the backup file, because ids are not guaranteed to align across systems. Idempotency tests that round-trip through `getBackupData()` will not catch id-misalignment bugs on their own — be deliberate about testing the name/title resolution path.

## Metadata

`Metadata` is intentionally **not** an entity — it does not follow the entity pattern and does not have the four-layer stack in the same way. It is a pseudo-DTO (a small key/value record) and is therefore allowed to be public and used directly in the API layer. Like the rest of the system, its timestamps use `java.sql.Timestamp`, which serializes to epoch milliseconds.

## Response Body Format

Response bodies are modeled after the JSON:API format. Every response body has two attributes:

- `data` — the payload on success, `null` on failure.
- `errors` — `null` on success, otherwise one or more messages.

This makes intent unambiguous: a successful `DELETE`, for example, returns `data: null` and `errors: null` — there is simply nothing to return, and the null `errors` confirms the request succeeded.

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

`secured` is an **overlay** profile: it is always activated alongside a datasource profile (`docker,secured` in `compose.secured.yaml` and in production, `{"test-container", "secured"}` in the secured test suites) and it is the single switch between the two builds of the app:

- **default (permit-all)** — no authentication; every request is anonymous and resolves to the default showcase owner, and `AccessService` reports full access. This preserves the original single-user behavior.
- **`secured`** — the app is an OAuth2 resource server and the capability matrix is enforced (see [Authentication](#authentication-keycloak-and-oauth-21) and [Roles and Capabilities](#roles-and-capabilities)).

Row-Level Security is **not** gated by the profile — it runs identically in both builds; only the resolved owner id differs. `GET /v1/heartbeat` reports which build is running (`{"message": "thump thump", "secureMode": true|false}`), which is how the front end and the MCP sidecar discover the server's posture without a token.

The resource-server settings live in `application-secured.properties` (`pensieve.oauth2.issuer`, `pensieve.oauth2.jwk-set-uri`, `pensieve.oauth2.audience`, each env-overridable as `PENSIEVE_OAUTH2_*`). `entitlement.trial-days` (env `ENTITLEMENT_TRIAL_DAYS`, default 30) is global.

## Authentication (Keycloak and OAuth 2.1)

**Keycloak is the single authorization server for both the web app and MCP**, and the API is a pure **OAuth 2.0 resource server** — it mints no tokens, stores no passwords, and has no login, registration, or refresh endpoints. That reimplementation (migration `V1_19`) came out of the MCP rollout: MCP hosts require standard OAuth 2.1 discovery/DCR/PKCE, which the previous homegrown HS256 login stack could not provide, so both surfaces were moved onto one issuer.

### Token validation

The `secured` chain (`api/security/SecurityConfig`) is stateless and validates Keycloak **RS256** access tokens. `OAuth2ResourceServerConfig` builds the `JwtDecoder` from a **JWKS URI plus an explicit issuer validator** rather than `issuer-uri` discovery — deliberately, because the API sits on a private compose network and cannot reach the host-facing issuer URL to run OIDC discovery. So:

- **`iss`** is validated against the canonical, host-facing issuer that Keycloak stamps into tokens (`KC_HOSTNAME`).
- **keys** are fetched over the internal network (`http://keycloak:8080/...`).
- **`aud`** is validated by `AudienceValidator` against the shared `/mcp` resource URI. Keeping audience validation on even behind a private network blocks the confused-deputy attack: a token minted for another resource server in the same realm cannot be replayed here. **The same audience is validated by the MCP sidecar**, so a mismatch (including an unsubstituted `${PENSIEVE_...}` placeholder) rejects every request on both sides.

Keycloak does **not** honor the RFC 8707 `resource` parameter, so the audience is attached by an **Audience mapper** on the `pensieve:read` client scope in the realm import — that is why tokens carry `aud` at all.

### Identity: token → `users` row

There is no user table in the API's own sense of "credentials" — `users` is the authorization/profile record, linked to a Keycloak account by the immutable `sub` claim (`users.keycloak_sub`, nullable + UNIQUE, `V1_19`). `OwnerResolver` resolves it in this order, and the details (claim-by-email, JIT trial provisioning, and their guard rails) are covered under [Roles and Capabilities](#roles-and-capabilities):

1. `keycloak_sub` matches → that row (its `email` re-syncs from the verified token).
2. Otherwise a **verified** `email` matches an existing row → claim it, stamping the `sub`.
3. Otherwise → **JIT-provision** a new trial account.
4. A token with no `email` claim (a service account, or one minted without the email scope) → **403**.

### The public read surface

Under `secured`, these routes are `permitAll` so an anonymous visitor can browse a public showcase without a token; everything else is `authenticated()`:

| Route | Methods |
| --- | --- |
| `/v1/heartbeat` | GET |
| `/v1/{entity}/*` (read by id, all six entities) | GET |
| `/v1/{entity}/function/search` | POST |
| `/v1/filters/**` | GET |
| `/v1/function/counts` | GET |
| `/v1/custom_fields`, `/v1/custom_fields/entity/*` | GET |
| `/v1/metadata/` + one of `ui-settings`, `default_sort_options`, `saved-filters`, `saved-filter-categories` | GET |
| `/v1/showcases` (the public directory) | GET |

Entity routes are enumerated per entity rather than wildcarded so future non-entity routes are not exposed by accident, and only the four metadata keys the showcase renders from are opened (not a `/v1/metadata/*` wildcard, nor the list-all GET) so a visitor cannot enumerate an owner's other metadata. Writes are never in this list: they fall through to `authenticated()` (anonymous → 401) and, for an authenticated showcase view, are then refused by the capability gate (403).

### Keycloak realms

The realm is fully declarative and imported on first boot; there is no manual post-import step. `keycloak/import/pensieve-realm.json` is the **dev** realm (used by both compose and the test Keycloak container) and `keycloak/import-prod/pensieve-realm.json` is the **production** realm — see [Deployment](#deployment-production-topology) for how they differ. `keycloak/README.md` documents the clients, scopes, and how to mint a token by hand.

**Password reset is Keycloak's, not the API's.** Both realms set `resetPasswordAllowed` ("Forgot Password?" on the hosted login page) plus an `smtpServer` — the `mailpit` container in dev (read the mail at <http://localhost:8025>), a real relay via `PENSIEVE_SMTP_*` in prod. No Java or front-end code participates: the API is a resource server and the web app only redirects to Keycloak's hosted pages.

**Neither realm sets `verifyEmail`** — deliberately, in dev *and* prod. An account's address is there for password reset and admin action emails; it is not a gate on signing in, so an unconfirmed address still logs in. What that leaves is a distinction worth keeping straight: Keycloak still carries a per-account `emailVerified` flag and still stamps it into tokens as `email_verified`, it just never sets it on its own any more. The API reads that claim in exactly one place — `OwnerResolver`'s claim-by-email path, which links a login to a pre-existing `users` row (see [Identity: token → `users` row](#identity-token--users-row) and the [admin bootstrap](#bootstrap-claim-the-seeded-default-showcase-row)) — and the guard there is unchanged. So an **admin-created account that must claim an existing row needs its address marked verified explicitly**, in the admin console or via `execute-actions-email`; an account that only provisions its own fresh row does not care. `keycloak/README.md` covers the flags, the onboarding call, and what this changed about `KeycloakTestSupport.passwordGrantUnverified`.

Note the realm import only runs on a **first** boot, and both compose stacks give Keycloak a persistent store — editing a realm file does not change an already-running realm. Apply realm changes in the admin console (or via a partial import) *and* to the JSON, so a fresh environment comes up the same.

## Multi-Tenancy and Row-Level Security

### The problem

The system began as a single-user portfolio app where every row was visible to everyone. To run it as a hosted, paid service, each user's collection must be **isolated** — one account can never see or modify another's data — while one curated collection stays **publicly readable** as a showcase for the marketing/guest site. The isolation needs to be strong enough that it holds even against a hand-written `SELECT *`, not just against the application's own queries.

### The model: owner_id + PostgreSQL Row-Level Security

Every tenant-scoped table (`systems`, `toys`, `video_games`, `video_game_boxes`, `video_game_to_video_game_box`, `board_games`, `board_game_boxes`, `custom_fields`, `custom_field_options`, `custom_field_values`, `metadata`) carries an `owner_id` referencing `users(id)`. Isolation is enforced **in the database** with Row-Level Security (RLS), so it cannot be forgotten in application SQL. Each table has one `FOR ALL` policy:

```sql
USING       (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int)
WITH CHECK  (owner_id = NULLIF(current_setting('app.current_owner', true), '')::int)
```

`USING` gates which rows are readable/updatable/deletable; `WITH CHECK` gates inserted/updated rows. The current owner is read from a per-request **session variable**, `app.current_owner`. When it is unset the predicate is `NULL` → false: no rows visible, no writes allowed (**fail-closed**). There is deliberately **no showcase carve-out** in the policy — an authenticated user sees only their own rows; the showcase is reached only because anonymous requests are *resolved to* the showcase owner (below).

### Why a separate database role

The application connects to Postgres as a **superuser**, and superusers bypass RLS even with `FORCE`. So the migration (`V1_14`) creates a dedicated, privilege-limited role **`app_rls`** (`NOLOGIN NOSUPERUSER NOBYPASSRLS`) that is granted only DML on the tenant tables — notably **no** access to `users`. Each request *assumes* this role for the duration of one transaction; because `app_rls` is a non-superuser, the policies actually bind.

### The per-request flow

The boundary is established by `TenantTransactionFilter` in `api/tenant/`, registered to run **after** Spring Security (so the `SecurityContext` is populated). For each tenant-scoped request it:

1. **Resolves the owner** (`OwnerResolver`) — *before* dropping privileges, since this reads `users`: the authenticated user's id (resolved from the token's `sub` claim — matched to `users.keycloak_sub`, claimed by `email` on first login when the token's `email_verified` claim is true, or JIT-provisioned as a trial otherwise), or the seeded **showcase owner** for an anonymous request. A valid token no account can be resolved or provisioned for — no `email` claim at all (a service account, or a token minted without the email scope), or an email that collides with an account linked to a different login — is answered **403** in the standard envelope, written directly by the filter (a thrown exception here would bypass the JSON error advice).
2. **Opens a transaction** and calls `TenantSessionRepository.assumeTenant(ownerId)`, which on that connection runs `SET LOCAL ROLE app_rls` and `set_config('app.current_owner', <id>, true)`. Both are transaction-local, so nothing leaks across pooled connections. (That repository owns no table — it configures the *session* — which is why it sits in `api/tenant/` next to the filter rather than under `domain/`. It exists so the filter itself issues no SQL: all database access goes through a repository.)
3. **Proceeds the chain inside that transaction.** Because the tenant session was established on the thread-bound transactional connection, every repository call and every `@Transactional` service method observes the role + owner, and RLS scopes all of it.

Four paths are **skipped** by the filter (`shouldNotFilter`) because they read or write `users`, which `app_rls` cannot touch, so they must run with the application's normal privileges:

| Path | Why it is safe outside the tenant transaction |
| --- | --- |
| `/v1/auth/**` | Only `GET /me`; resolves the caller explicitly and returns their own identity. |
| `/v1/heartbeat` | Touches no data at all. |
| `/v1/admin/**` | `AdminController` authorizes the caller as ADMIN itself, using the **no-arg** `OwnerResolver.resolveOwner()` (so impersonation cannot reach the control plane). |
| `/v1/showcases` | Public directory; exposes only slug + display name. |

Alongside the owner id and role, the filter stashes a third flag in `TenantContext`: **`showcaseView`**, true only for an `X-Showcase` request. It is deliberately distinct from `role == GUEST` (the permit-all build resolves every anonymous caller to GUEST too), so the read overrides keyed on it never affect the single-user build — see [Public showcases](#what-a-showcase-view-actually-sees).

### Insert stamping and the showcase owner

`owner_id` is never set by application code. Each column has a DEFAULT that stamps it from the session:

```sql
owner_id INTEGER NOT NULL DEFAULT
    COALESCE(NULLIF(current_setting('app.current_owner', true), '')::int, showcase_owner_id())
```

So ordinary inserts are stamped with the current owner, and writes that have no request context (the migration's own backfill, or raw `@JdbcTest` inserts) fall back to the showcase owner. The **showcase owner is identified by a flag, not a hard-coded id**: `users.is_public_showcase` (a partial unique index guarantees exactly one). It is resolved on demand — by the `showcase_owner_id()` SQL function in DEFAULTs, and by `OwnerResolver` (cached) for anonymous requests — so the SERIAL id can differ per environment without anything breaking.

### Interaction with tests

`@JdbcTest` repository tests load only the JDBC slice, so the filter never runs; they connect as the superuser (bypassing RLS) and their inserts are stamped to the showcase owner by the COALESCE fallback — so they needed no changes. The dedicated tenancy tests instead `SET LOCAL ROLE app_rls` and set `app.current_owner` explicitly to prove isolation holds — including against a raw `SELECT *`: `domain/tenant/RowLevelSecurityTests`, `RepositoryRowLevelSecurityTests`, and `UsersBillingColumnsTests` run on the `rls-tests` profile, and `controllers/MultiTenancyTests` drives the whole stack under `{"test-container", "secured"}`.

### Known limitation / future hardening

Because the app logs in as a superuser, isolation depends on *every* tenant DB access happening inside the request transaction that assumed `app_rls`. Any code path that touches tenant tables outside it (a future `@Scheduled`/CLI job) would run as the superuser and bypass RLS, and must demote explicitly. The complete fix is to have the application **log in as a non-superuser role** (with Flyway migrating as the owner); then RLS binds unconditionally and the footgun disappears.

### Switching the backend between secured and unsecured modes but connected to the same database

RLS runs **identically in both profiles** — `TenantConfig` registers `TenantTransactionFilter` on `@ConditionalOnWebApplication` alone, with no `@Profile` gate — so the only thing the `secured` profile changes is *which owner id gets resolved*, never whether isolation is enforced. Unsecured requests are anonymous, so `OwnerResolver` resolves every one of them to the single **default-showcase owner** (the `is_public_showcase` row); its inserts are stamped with that id, and RLS scopes its reads to that id.

This matters in one edge case: running the app in `secured` mode, writing data, then pointing an **unsecured** instance at the same database. It is unlikely (a database should not switch security modes under real data), but if it happens, the behavior is **as intended — the unsecured instance sees only the data written in unsecured mode**, i.e. only rows owned by the default-showcase owner. Data written by separately registered `secured`-mode accounts carries their own distinct `owner_id`, so RLS makes it **invisible and unwritable** to the unsecured requests (and, fail-closed, they can never widen that scope). No changes are needed to protect the secured data; the database boundary already does it.

The one caveat is that the isolation key is precisely *"rows owned by the `is_public_showcase` user,"* which equals *"data written in unsecured mode"* only while no `secured`-mode account writes as that same row. The [admin bootstrap](#bootstrap-claim-the-seeded-default-showcase-row) deliberately **claims the default-showcase row** as the operator's account — so anything that admin writes in `secured` mode shares the default-showcase `owner_id` and *would* appear in unsecured mode (and vice-versa). That is by design (the showcase owner *is* the public collection), not a leak of other users' data. If you ever need strict separation even in this scenario, bootstrap the `secured`-mode admin as a **separate registered account** and leave the `is_public_showcase` row unclaimed, so no `secured`-mode write is ever stamped with the unsecured owner id.

## Roles and Capabilities

A backend-owned **role** model gates capability per request. It is the deliberate "work around the payment processor" design: a role is derived from fields an operator can set by hand long before any Paddle integration exists, and Paddle (a later phase) will simply automate writes to the billing fields the derivation reads.

### The fields and the rule

`users` carries `plan` (`free`/`paid`), `subscription_status` (`trialing`/`active`/`past_due`/`canceled`/null), `access_until` (a timestamp), nullable Paddle ids + `last_event_id` (reserved for the future webhook) — all from migration `V1_15` — plus a nullable `role_override` (`V1_16`). Each request resolves to one of five roles:

```
role_override != NULL                                  -> Role.valueOf(role_override)   // admin pin
access_until in future + subscription_status='trialing' -> TRIAL
access_until in future                                  -> PAID
otherwise (authenticated)                               -> LAPSED
anonymous request                                       -> GUEST
```

`plan` is informational / for Paddle reconciliation; trials stay trivial (registration just sets `access_until` + `subscription_status='trialing'`, `plan` stays `free`). What each role may do is the **capability matrix** in `AccessService` (the single source of truth):

| Capability | GUEST | TRIAL | PAID | LAPSED | ADMIN | Denied |
|---|:--:|:--:|:--:|:--:|:--:|---|
| READ | ✓ | ✓ | ✓ | ✓ | ✓ | — (RLS-scoped) |
| FILTER | ✓* | ✓ | ✓ | ✗ | ✓ | 402 |
| WRITE | ✗ | ✓ | ✓ | ✗ | ✓ | 403 |
| BACKUP | ✗ | ✓ | ✓ | ✓ | ✓ | 403 |
| IMPORT | ✗ | ✗ | ✓ | ✗ | ✓ | 403 |
| ACCESS_ADMIN | ✗ | ✗ | ✗ | ✗ | ✓ | 403 |

`*` GUEST reads/filters whichever showcase the request resolves to — the default showcase for an anonymous no-header request, or the showcase named by an `X-Showcase: <slug>` header (see [Public showcases](#public-showcases)); GUEST writes/backup/import on anonymous requests are blocked at Spring Security (401) before capabilities apply, and on an authenticated showcase view (`X-Showcase` set) by the capability matrix (403).

### Where it is enforced

The role is resolved once per request in `OwnerResolver.resolveOwner()` (via the pure `deriveRole(User)`) — in the **same `users` lookup that resolves the owner id, before the connection drops to `app_rls`** (which has no grant on `users`). It is stashed in `TenantContext` alongside the owner id. `AccessService.can(...)` reads that request-scoped role (never the DB, so it is safe inside the demoted transaction) and the gates live at the semantic chokepoints — all four of them:

| Gate | Capability → status |
| --- | --- |
| `EntityGatewayAbstract.getWithFilters` | FILTER → 402 |
| `EntityGatewayAbstract` writes | WRITE → 403 |
| `CustomFieldGateway` writes | WRITE → 403 |
| `MetadataGateway` writes | WRITE → 403 |
| `BackupImportGateway.getBackupData` | BACKUP → 403 |
| `BackupImportGateway.importBackupData` | IMPORT → 403 |

Reads-by-id are ungated (RLS already scopes the row), as are custom-field and metadata **reads** — a showcase visitor must be able to render the owner's custom-field columns and configured sort. Enforcement is **only active under the `secured` profile** — the default permit-all build reports full access, preserving the single-user behavior.

New accounts are auto-granted a trial on **first login via JIT provisioning in `OwnerResolver`**: when a token's `sub` has no matching row (and no `email` match to claim), a new `users` row is inserted with `access_until = now + entitlement.trial-days` (default 30, env `ENTITLEMENT_TRIAL_DAYS`) and `subscription_status='trialing'`, so it resolves to **TRIAL**. Guard rails on this path: **claim-by-email requires the token's `email_verified` claim** (an unverified address must not take over a seeded row); email matching is **case-insensitive** (Keycloak lowercases emails, seeded rows are typed by hand); a sub-linked row's **`email` re-syncs from the (verified) token on each login**, so it tracks address changes made at the IdP; and a token with **no email claim** is rejected 403 rather than reaching the JIT insert's NOT NULL constraint. (`GET /v1/function/counts` is ungated like reads-by-id — RLS already scopes the aggregate.)

### Admin role management

Admins are themselves a role (pinned via `role_override`), and there is **exactly one** — the operator. The `uq_users_single_admin` partial unique index (`V1_17`) allows at most one row pinned `'ADMIN'`: pinning a second via the API is a 400 ("An admin already exists. Clear the current admin's role override first."), and manual SQL fails hard at the index. Self-demotion of the only admin is allowed (no lockout logic); the bootstrap below is the recovery path. The admin API:

- `GET /v1/admin/users` — list accounts with their effective role, `role_override`, billing fields, and `showcaseSlug`/`showcaseName`.
- `POST /v1/admin/users/{id}/role` — set `role_override` to one of the five roles, or `null` to revert to auto-derivation.
- `POST /v1/admin/users/{id}/showcase` — grant/clear a public showcase (see [Public showcases](#public-showcases)).

These routes bypass the tenant transaction filter (they read/write `users`, which `app_rls` cannot touch) and authorize the caller as ADMIN inside the controller.

#### Bootstrap: claim the seeded default-showcase row

There is no seed/env admin. The operator **claims the seeded `showcase@internal.local` row** (the `is_public_showcase` marker row that owns all pre-existing data) as their own account — it becomes, at once, the single ADMIN, the default showcase's owner, and an ordinary data owner, so the admin logs in and edits the default showcase as their own collection. All pre-existing `owner_id` FKs and the `showcase_owner_id()` function keep working untouched.

Credentials now live in Keycloak, not in `users` (the legacy `password_hash` and `enabled` columns were dropped in `V1_20`). The row is claimed by **email on first login**: point the seeded row's `email` at the operator's Keycloak account, and their first authenticated call stamps that row's `keycloak_sub`. **One-time manual procedure per environment (never automated in a migration):**

1. Create the operator's account in **Keycloak** (the same realm the backend validates against), with their intended email and password — and mark the email **verified** (admin console → user → Email verified), or send them a `VERIFY_EMAIL` + `UPDATE_PASSWORD` action email and let them set their own password (see `keycloak/README.md`). **Do not skip this**: claim-by-email requires the token's `email_verified` claim, and since neither realm sets `verifyEmail`, Keycloak will not set the flag for you — an admin-created account is unverified until someone says otherwise. Such an account logs in fine but does not claim the row; the request is refused with a 403 email-conflict error instead, which is the confusing symptom this step exists to prevent.
2. Point the seeded row at that email and pin it ADMIN via SQL (`showcase_slug`/`showcase_name` are already seeded by `V1_18`):
   ```sql
   UPDATE users
   SET email = 'you@domain.com',
       role_override = 'ADMIN'
   WHERE is_public_showcase;
   ```
3. Log in once as that Keycloak account — the first authenticated call **claims the row by email**, stamping its `keycloak_sub` so subsequent logins resolve by `sub`.

No billing window is needed — `role_override='ADMIN'` wins in the derivation regardless of `access_until`, so the admin never lapses. The credential lives only in Keycloak. (This supersedes the earlier "promote any user by email" bootstrap; the claim is exercised end-to-end by `ShowcaseSecuredProfileTests.claimedDefaultShowcase_AdminEditsItAsTheirOwnCollection`.)

Until the Paddle webhook lands, you can also drive the billing fields directly to grant/revoke access (the role re-derives without a pin):

```sql
-- Grant/extend 1 year of paid access:
UPDATE users SET plan = 'paid', subscription_status = 'active',
    access_until = now() + interval '1 year' WHERE email = 'customer@example.com';

-- Revoke (account becomes LAPSED on its next request):
UPDATE users SET subscription_status = 'canceled', access_until = NULL
    WHERE email = 'customer@example.com';
```

Because the role is resolved per request, any of these changes take effect on the account's very next request — no re-login required.

### Impersonation ("act as user")

An admin can **act as any user** — see exactly what that user sees and operate inside their tenant — for support and troubleshooting. It is driven by a stateless request header, `X-Act-As-Owner: <userId>`: the admin keeps their own token and the front end attaches the header while impersonating. **Start** = begin sending the header; **stop** = stop sending it. There is no server-side impersonation session and no change to the JWT — the header is not a credential.

**Full act-as, not read-only.** While impersonating, the request adopts the *target's* effective role, so the capability matrix scopes the admin to exactly what that user could do (impersonating a PAID user permits WRITE/BACKUP/IMPORT; a LAPSED user does not). This was a deliberate choice over an earlier read-only sketch: an admin troubleshooting an account often needs to *fix* its data, not just look, and reusing the target's real role keeps the behavior identical to what the user themselves would experience — no separate "impersonation capability set" to keep in sync with the matrix.

#### How the auth check works

This is the subtle part: **two separate checks run against two different identities.**

1. **The impersonation gate — checked against the real admin.** Impersonation never changes who is *authenticated*; the request is still authenticated by the admin's own `Bearer` token, so the Spring Security principal is always the admin. The header is only *honored because* that authenticated caller is already an ADMIN. The gate lives in `OwnerResolver.resolveOwner(String header)`: it resolves the real caller first, then proceeds only if `accessService.can(caller.role(), Capability.ACCESS_ADMIN)` — the pure matrix lookup against the caller's *own* role. A non-admin (or anonymous → GUEST) sending the header fails this check and simply acts as themselves. This is the only place "may this caller impersonate at all?" is asked, and it is always asked of the admin.

2. **The per-operation check — checked against the impersonated target.** Once the gate passes, the resolver returns an `OwnerContext` carrying the *target's* id and `deriveRole(target)`, plus an `Impersonator` record naming the real admin. `TenantTransactionFilter` stashes the **target's** role in `TenantContext` and sets `app.current_owner = targetId`. So every actual operation is authorized as the target: the capability chokepoints (`EntityGatewayAbstract`, `BackupImportGateway`) call `AccessService.can(Capability)`, which reads the target's role from `TenantContext`, and RLS independently scopes all rows to `targetId`. That is what makes it full act-as.

| Question | Whose identity answers it | Where |
|---|---|---|
| Authenticated? | Always the admin (their access token) | Spring Security OAuth2 resource server (`JwtDecoder`) |
| May this caller impersonate? | The admin (`ACCESS_ADMIN`) | `OwnerResolver.resolveOwner(header)`, once, up front |
| May this operation proceed (WRITE/FILTER/…)? | The **target** | `AccessService.can(Capability)` ← `TenantContext` role |
| Which rows are visible/mutable? | The **target** | RLS via `app.current_owner` |

Because impersonation only flows through the same `OwnerResolver` → `TenantContext` → RLS path that the whole tenant boundary already uses, it required no new enforcement code — just resolving a different owner + role when the gate passes.

#### Reporting: `GET /v1/auth/me`

`/me` reports the caller's identity, effective role, and `accessUntil` — the access-window expiry as **epoch milliseconds** (the same `access_until` that drives the TRIAL/PAID/LAPSED derivation), so the front end can show how long the plan stays active. It is `null` when the account has no window (e.g. an admin-pinned role), and it is typed as an explicit `Long` so the wire value is an unambiguous number rather than whatever Jackson would do with a `Timestamp`.

It also lets the front end render an impersonated target's view while still showing that an admin is driving. During impersonation the primary `id`/`email`/`role`/`accessUntil` are the **admin's** (role always `ADMIN`, and the admin's own window — not the target's), and the target is nested under an `impersonating` object (`id`, `email`, `role`); for a normal request `impersonating` is `null`.

```json
{ "id": 1, "email": "admin@x.com", "role": "ADMIN", "accessUntil": null,
  "impersonating": { "id": 42, "email": "user@x.com", "role": "PAID" } }
```

#### Edge cases and why

- **Lenient resolution.** A missing/blank/non-numeric header, a non-admin caller, or an unknown target id all fall through to the real caller rather than erroring. This is deliberate: impersonation is resolved inside the servlet filter, *before* `DispatcherServlet`, where a thrown exception would bypass the `@ControllerAdvice` JSON error envelope and surface as an ugly 500. The trusted admin front end picks targets from `GET /v1/admin/users`, and `/me` always reflects the *actual* acting identity (no `impersonating` marker if the header didn't take), so a no-op is observable by the client. The trade-off: an admin impersonating a *deleted* user id silently acts as themselves — a well-behaved front end detects this via `/me`.
- **The admin control-plane is unaffected.** `/v1/admin/**` bypasses the tenant filter and authorizes via the **no-arg** `OwnerResolver.resolveOwner()`, which ignores the header — so an admin can't lock themselves out of the admin API while a header is set, and impersonation never escalates onto the role-management routes.
- **Secured profile only.** Under the default permit-all build there is no authentication, so every caller resolves to GUEST and the gate (`ACCESS_ADMIN`) is never met — the header is inert.

Implemented in `api/tenant/` (`OwnerResolver`, `OwnerContext`, `Impersonator`, `TenantTransactionFilter`, `TenantSessionRepository`), `AuthController.me()` + `MeResponseDto`/`Impersonation`, and exercised by `controllers/AdminImpersonationSecuredProfileTests`.

## Public Showcases

**A showcase is a paid user's own collection, made public.** There is no separation between "personal data" and "showcase data" — granting a showcase publishes the collection as-is, and the owner keeps editing it with their normal role's WRITE (no new write path, no new role). Multiple showcases are just multiple users with public collections.

### The model — one column, three concerns

- **`users.showcase_slug` (nullable, UNIQUE, `V1_18`) — the entitlement *and* the address.** Non-null means the collection is public, and the value is where it lives; there is no inconsistent "public but unaddressable" state. Format: `^[a-z0-9](-?[a-z0-9])*$`. `showcase_name` is the display title the public ever sees (never the owner's email).
- **`plan` — the (future) tier.** Hosting a showcase is a higher paid tier than plain PAID; for now entitlement *is* the admin granting a slug by hand (`POST /v1/admin/users/{id}/showcase`, backend-authoritative). A later Paddle product maps its price to `plan='showcase'` and the visibility rule gains that clause — which preserves a slug across a showcase→paid downgrade (address reserved, showcase hidden).
- **Derived role — the lapse gate.** A slug **resolves only while its owner derives to PAID or ADMIN**. A lapsed (or trial) owner's slug stays reserved in the database but resolution 404s — the showcase is a renewal hook.

### Viewing: the `X-Showcase: <slug>` header

Viewers switch showcases per request; the BFF maps its `/s/{slug}` routes to the header. Resolution in `TenantTransactionFilter`/`OwnerResolver.resolveShowcase`:

```
X-Showcase header present:
    slug resolves AND owner derives to PAID/ADMIN  -> (thatOwner.id, GUEST)   [even if authenticated]
    slug unknown / owner not PAID-or-ADMIN         -> 404, written by the filter
no X-Showcase header:
    authenticated                                  -> (caller.id, deriveRole(caller))  [X-Act-As-Owner may apply]
    anonymous                                      -> (defaultShowcaseOwnerId, GUEST)
```

- **The header wins for every caller, authenticated ones included** — logged-in users browse showcases without logging out — and is always **GUEST-scoped**: read + filter only; writes and backup are 403. It also **wins over `X-Act-As-Owner`** when both are present (an explicit read-only view request); writable impersonation is a separate path.
- The 404 is written directly by the servlet filter (an exception there would bypass the JSON error envelope), with one message for "unknown" and "not visible" so responses don't leak whether a slug exists.
- `GET /v1/showcases` — a public (`permitAll`, tenant-filter-bypassing) **directory** of every visible showcase as `{slug, name}`, backing the front end's switcher. A showcase drops out of the directory exactly when its slug stops resolving.

### What a showcase view actually sees

A showcase view is more than "GUEST + a different owner id": the request also carries the `showcaseView` flag (`OwnerContext.showcase()` → `TenantContext.isShowcaseView()` → `AccessService.isShowcaseView()`), which is what read overrides key on. It is intentionally **independent of the `secured` profile short-circuit** — unlike `can(...)`, it reports the truth in both builds — and is never set by the permit-all build's own anonymous requests, so the single-user deployment keeps seeing its own settings.

- **Entities** — read and filter only; the collection is the owner's rows, scoped by RLS.
- **Custom fields** — **read-only**. Definitions are readable (the showcase renders the owner's custom-field columns from them); create/update/delete requires WRITE, so a GUEST showcase view gets a 403. Exercised by `controllers/CustomFieldShowcaseSecuredProfileTests`.
- **Metadata** — read-only, and `ui-settings` is **substituted**, not passed through: `MetadataGateway` serves the fixed `ShowcaseMetadata.guestUiSettings()` (beginner mode on, every other mode off, both default views `"list"`, every standard field shown) so a visitor never sees the owner's personal editor state. Every other key — notably `default_sort_options` and the saved filters — passes through to the owner's own row via RLS, so a guest mirrors, and stays in sync with, the owner's configured showcase. The substitution applies to both `GET /v1/metadata` (the list) and `GET /v1/metadata/ui-settings`. Exercised by `controllers/MetadataShowcaseSecuredProfileTests`.
- **Counts** — `GET /v1/function/counts` works unchanged; it summarizes exactly the rows the public search endpoints already expose.

### The default showcase

The seeded `is_public_showcase` row is re-documented as the **default-showcase marker**: it is the fallback owner for anonymous no-header requests (and the non-secured build), and `V1_18` seeds its slug/name (`seths-collection` / "Seth's Collection"). The operator claims this row as the single admin (see the bootstrap above) and authors the default showcase as the owner of that data — the earlier idea of impersonating an unloggable showcase user to author it is superseded.

Implemented in `api/tenant/` (slug resolution + GUEST scoping), `ShowcaseController` (directory), `AdminController.setShowcase` (grants), migrations `V1_17`/`V1_18`, and exercised by `controllers/ShowcaseSecuredProfileTests` + `SingleAdminSecuredProfileTests`.

## MCP Sidecar

The **MCP sidecar** is a **read-only MCP (Model Context Protocol) server** that lets AI hosts — Claude Desktop, Claude Code, claude.ai connectors — answer natural-language questions about a collection. It lives in [its own repository](https://github.com/sethhaskellcondie/the-game-pensieve-mcp), whose README is the operational reference; this section is the design rationale, kept here because the sidecar's contract is defined by this API.

### It is a sidecar, not a module

It is a **separate TypeScript/Node process** that speaks MCP over **Streamable HTTP** and fulfills every tool call by calling the existing REST API over HTTP. Nothing about MCP leaks into the Java code — the sidecar is just another API consumer. That is what keeps the security story simple: MCP reads inherit *exactly* the authorization the web app has (RLS + the capability matrix), never more, because they travel the same routes with the same token.

The one accommodation the backend made for it is `GET /v1/function/counts` (`domain/counts/`), added so the `get_collection_summary` tool can describe the shape of a collection without transferring every row of every entity.

### Transport

**Stateless** Streamable HTTP: each `POST /mcp` builds a fresh `McpServer` + transport, serves the request, and tears both down. `GET`/`DELETE` on `/mcp` return **405** — those verbs exist for server-initiated SSE streams and session teardown, which a stateless server has no use for. `GET /healthz` is a liveness probe and stays public.

### The tool surface

All tools are read-only (`readOnlyHint`), and each maps to one REST endpoint:

| Tool | REST endpoint |
| --- | --- |
| `get_available_filters(entityKey)` | `GET /v1/filters/{key}` |
| `search_systems` / `search_toys` / `search_video_games` / `search_video_game_boxes` / `search_board_games` / `search_board_game_boxes` `(filters?)` | `POST /v1/{entity}/function/search` |
| `get_custom_fields(entityKey?)` | `GET /v1/custom_fields[/entity/{key}]` |
| `get_collection_summary()` | `GET /v1/function/counts` |
| `list_showcases()` | `GET /v1/showcases` |

Two conventions from this codebase have to be restated in the sidecar's `src/entities.ts` because it cannot import the `Keychain`: the six entity **keys** (`system`, `toy`, `videoGame`, `videoGameBox`, `boardGame`, `boardGameBox`), used verbatim in filter payloads and in the `/filters/{key}` and `/custom_fields/entity/{key}` paths, and the **pluralized controller paths** used by search (`videoGame` → `/v1/videoGames/...`). If you add an entity to the `Keychain`, add it there too — that is a cross-repository change, and the sidecar will silently lack the new entity until it is made.

The filter tool descriptions carry the operator list from [The Filter System](#the-filter-system), and `get_available_filters` is advertised as the call to make first, so the model builds valid filters instead of guessing. The entity `key` is injected by the sidecar rather than asked of the model. API errors come back as MCP `isError` results carrying the status and body — so a `402` (lapsed filter) or `403` (write capability) surfaces to the host as a readable message rather than a transport failure.

### OAuth: the sidecar is a protected resource

When enforcing, the sidecar is an **OAuth 2.0 Protected Resource** in its own right. It validates the incoming bearer with [`jose`](https://github.com/panva/jose) (signature via JWKS, plus `iss` and `aud`), publishes **Protected Resource Metadata** (RFC 9728) at `/.well-known/oauth-protected-resource[/mcp]` — served at both the root and path-aware URLs so clients following either convention discover the authorization server — and challenges a missing or invalid token with `401 + WWW-Authenticate: Bearer resource_metadata="…"`. A host then runs the standard OAuth flow (DCR + authorization code + PKCE) against Keycloak on its own.

A valid token is **forwarded** to the API, which validates it independently and scopes the request to its owner. Both sides check `aud` against the same `/mcp` resource URL; that is the confused-deputy guard, and it is why the audience value appears in the realm's Audience mapper, the sidecar's config, and the backend's config, and must match in all three.

Enforcement is chosen by `MCP_AUTH_MODE`:

| Mode | Behavior |
| --- | --- |
| `auto` (default) | enforce iff the backend heartbeat reports `secureMode=true` |
| `required` | always enforce — the production setting, with no probe dependency |
| `disabled` | never enforce (tokenless) |

`auto` exists so a developer's local stack works without configuration, and it is careful about two failure modes. The startup heartbeat probe is **retried** (`MCP_HEARTBEAT_RETRIES` × `MCP_HEARTBEAT_RETRY_DELAY_MS`, default 30 × 2s) because compose `depends_on` waits for start, not readiness — a sidecar that boots first must not latch enforcement off from one failed probe. And if `secureMode` is still undetermined after the retries **while OAuth is configured**, it **fails closed** (enforces, with a loud warning) rather than serving `/mcp` tokenless against a secured backend. Enforcement with incomplete OAuth config is a hard startup error.

As with the backend, `iss` is the canonical host-facing issuer while `MCP_OAUTH_JWKS_URI` points at the internal `keycloak:8080` URL; in production both are the public `https://` URLs.

### Running and testing it

From a clone of the [sidecar repository](https://github.com/sethhaskellcondie/the-game-pensieve-mcp):

```bash
npm install
npm run dev          # tsx watch
npm test             # vitest — hermetic, no backend or Keycloak needed
npm run typecheck
docker build -t sethcondie/the-game-pensieve-mcp:latest .   # to run local changes in compose
```

And from this repo, which consumes the sidecar as a published image:

```bash
docker compose -f compose.unsecured.yaml up -d backend mcp    # endpoint at http://localhost:8090/mcp
```

The vitest suite covers config resolution (including the fail-closed rule), the auth helpers against a locally minted JWKS, the HTTP app's challenge/405/metadata behavior, the API client, and tool registration — no live dependencies, so it runs in CI without Docker. Register the running sidecar with a host via `claude mcp add --transport http pensieve http://localhost:8090/mcp`, or point the MCP Inspector at it.

## Database and Migrations

Persistence is PostgreSQL 16, accessed through Spring's JDBC Template (no ORM). Schema changes are managed by **Flyway**; migrations live in `src/main/resources/migrations` and follow the `V{major}_{minor}__Description.sql` naming convention (e.g. `V1_5__CreateVideoGameTables.sql`). `spring.flyway.validateMigrationNaming=true` is on, so misnamed files will fail the build.

Conventions for new migrations:

- Never edit a migration that has already been applied anywhere — add a new one.
- Tables carry `created_at`, `updated_at`, and a nullable `deleted_at` (soft delete).
- Each migration includes commented-out "Undo" statements at the bottom for manual rollback reference.

## Deployment (production topology)

Production is defined by `compose.production.yaml`, `Caddyfile`, and `.env.production.example` (copy to `.env.production` and fill in). **Caddy is the only public service** — it terminates TLS and binds ports 80/443, reverse-proxying three hostnames to private services: the app (`frontend`), the MCP sidecar (`mcp`), and auth (`keycloak`). Everything else — `backend`, `db`, `keycloak`, `keycloak-db`, `mcp`, and `frontend` — is private with no published ports and is reachable only over the compose network.

**Production runs secured.** The backend is started with `SPRING_PROFILES_ACTIVE: docker,secured` and its `PENSIEVE_OAUTH2_*` env, the sidecar with `MCP_AUTH_MODE=required`, and Keycloak has its own Postgres (`keycloak-db`), separate from the app database. There is **no `flyway` service** here — the production backend runs Flyway on startup — and the app database keeps a named volume. It matches `compose.secured.yaml` in posture, and is the opposite of `compose.unsecured.yaml` (see [Security Mode in Docker](#security-mode-in-docker)).

Production Keycloak imports its **own realm file** — `keycloak/import-prod/pensieve-realm.json`, not the dev one. The prod realm ships with the dev-only surface removed: **no test users**, **no `pensieve-test-client`** (no public client, no direct-access grants), **no anonymous DCR** (remote MCP hosts are pre-registered via the admin console), and `sslRequired=external`. Its deployment-specific values — the `pensieve:read` Audience mapper's `https://<MCP_DOMAIN>/mcp` audience, the `pensieve-web` redirect URIs/origins, and the web client secret — are `${PENSIEVE_*}` placeholders that Keycloak resolves from the service environment at import time (wired from `.env` in `compose.production.yaml`), so there is **no manual pre-deploy realm edit**. The import runs once, on first boot with an empty `keycloak-db`. After the first deploy, decode an access token and verify `aud` and `iss`: the audience is validated by **both** the MCP sidecar and the backend resource server, so a mismatch (including a literal unsubstituted `${PENSIEVE_...}`) rejects every request.

## Testing Strategy

The project uses a **diamond testing strategy**: a broad layer of integration tests that exercise the stack from the controller down, plus focused unit tests for the parts that need more rigor (notably custom fields and filters).

- Integration tests use **MockMvc** (bundled with Spring Boot) to drive the controllers.
- They run against **Testcontainers** so each run gets an isolated Postgres instance with no cross-contamination between tests. **Docker must be running** for these tests.
- The filter integration tests are split across the `filter-tests1`–`filter-tests8` profiles to spread the container load.

### The secured-profile suites

Tests that exercise authentication (`*SecuredProfileTests`, `MultiTenancyTests`, `SeededDataMatrixTests`) run against a **real Keycloak Testcontainer** rather than mocked tokens — they mint genuine RS256 access tokens and the app validates them exactly as it would in production:

- `KeycloakTestSupport` owns the container: a JVM-wide singleton started on first use and reused for the whole run (Ryuk reaps it). It mounts **the same realm file the compose stack uses** (`keycloak/import/pensieve-realm.json`, by host path — no duplicated copy to drift), so its tokens carry the real audience, the `pensieve:read` scope, and `sub`/`email`. Tokens are minted through the realm's public `pensieve-test-client` with the direct-access (password) grant, and `ensureUser` admin-creates accounts on demand so tests keep their familiar "make a user, get a token" shape.
- `SecuredProfileTest` is the mix-in base that points `pensieve.oauth2.issuer`/`jwk-set-uri` at that container via `@DynamicPropertySource`. Subclasses keep their own `@SpringBootTest`/`@ActiveProfiles` because the datasource profile differs (`test-container` vs `seeded-tests`); the audience stays the fixed value from `application-secured.properties`.

### The MCP sidecar suite

The sidecar has its own **vitest** suite (`npm test`, run from its repository), independent of the Maven build and **hermetic** — no backend, database, or Keycloak needed, so it runs without Docker. It covers config resolution and the fail-closed enforcement rule, token verification against a locally minted JWKS, the HTTP app (auth challenge, metadata endpoints, 405s), the API client, and tool registration.

> On some machines not all containers start reliably. If the suite fails for that reason, reduce the load by commenting out the `GetWithFilters...Tests.java` series.

## Seeding Multi-Role Test Data

The single-user seed endpoints (`/v1/function/seedSampleData`, `/seedMyCollection`) only populate one owner. To exercise every role (GUEST, TRIAL, PAID, LAPSED, ADMIN) and the showcase-switching features against realistic multi-user data, there is a **multi-role seed set** with **two consumers** that must never share a database:

1. **Integration tests** — `SeededUsersFixture` seeds through MockMvc inside the test JVM; `SeededDataMatrixTests` asserts the capability/showcase matrix against it.
2. **Live environments (dev/staging)** — `scripts/seed-test-data.sh` runs the same choreography over real HTTP for manual testing, front-end work, and smoke checks.

Both load the same eight seed files from `src/main/resources/seeders/` and perform the same choreography, so they never drift. Never point the script at the integration-test database (the suite seeds itself), and never make a test depend on an externally pre-seeded database.

### What gets seeded

One bootstrap admin (`seeder-admin@email.com` / `seeder-admin` by default), eight users, two public showcases, and a populated default showcase (via `sampleData.json`):

| Email | Password | Pinned role | Seed file | Showcase |
| --- | --- | --- | --- | --- |
| `trial1@email.com` / `trial2@email.com` | `trial1` / `trial2` | TRIAL | seedTrialData1/2.json | — |
| `paid1@email.com` / `paid2@email.com` | `paid1` / `paid2` | PAID | seedPaidData1/2.json | — |
| `lapsed1@email.com` / `lapsed2@email.com` | `lapsed1` / `lapsed2` | LAPSED | seedLapsedData1/2.json | — |
| `showcase1@email.com` / `showcase2@email.com` | `showcase1` / `showcase2` | PAID | seedShowcaseData1/2.json | `showcase-one` / `showcase-two` |

### The choreography (shared by both consumers)

1. **Bootstrap the admin:** create its Keycloak account, log in once so the first authenticated call JIT-provisions its `users` row, pin it with the one SQL statement the API cannot perform (`UPDATE users SET role_override='ADMIN' WHERE email=...`), and log in.
2. **Per user:** create the Keycloak account (tolerating an already-existing account on re-runs) → log in as the user once so the **first authenticated call JIT-provisions the `users` row** (a fresh row derives to TRIAL, which lacks IMPORT) → admin pins **PAID** → the user `POST /v1/function/import`s the seed file wrapped under a `"data"` key (the user's own token makes RLS stamp every row to that owner) → admin pins the **final role**. Final roles are always pinned, never cleared back to derivation — a derived TRIAL silently lapses when its `access_until` window passes, rotting the fixtures.
3. **Showcase grants:** `POST /v1/admin/users/{id}/showcase` for the two showcase users (the slug *is* the entitlement; they stay pinned PAID so the showcases remain visible).
4. **Default showcase:** the seeded `showcase@internal.local` row is unloggable, so the admin imports for it through writable impersonation — pin it PAID (impersonation adopts the *target's* role; unpinned it derives to LAPSED, and the import would 403), `POST /v1/function/seedSampleData` with `X-Act-As-Owner: <its id>`, then clear the pin.

Everything is idempotent and rerunnable: registration tolerates "already exists", imports resolve existing rows by name/title, and pins/grants re-apply cleanly.

### Running the integration-test consumer

```bash
./mvnw test -Dtest=SeededDataMatrixTests
```

Docker must be running (Testcontainers). The suite runs under its own `seeded-tests` Spring profile — a dedicated Testcontainers database, mirroring the `filter-tests`/`import-tests` pattern — so its fixed emails and slugs never collide with the other suites, and it seeds **once per class** (eight ~220KB imports are too heavy per test). It also runs as part of the full `./mvnw test` suite. To seed the same data in another test class, reuse `SeededUsersFixture` under the same `{"seeded-tests", "secured"}` profiles.

### Running the live-environment consumer

```bash
# Against the local SECURED compose stack — the unsecured stack cannot be seeded (see below):
docker compose -f compose.secured.yaml up -d
./scripts/seed-test-data.sh

# Everything is parameterized via environment variables. Any target realm must have a client with
# direct access grants enabled (see the third precondition below):
BASE_URL=https://dev.example.com \
KEYCLOAK_URL=https://auth.dev.example.com KEYCLOAK_CLIENT=some-direct-grant-client \
ADMIN_EMAIL=ops@example.com ADMIN_PASSWORD='...' \
SQL_CMD="psql -h dev-db.example.com -U postgres -d pensieve-db" \
./scripts/seed-test-data.sh
```

Requires `curl` and `jq`. Preconditions — the script checks each one in Step 0 and stops with a message naming the actual cause, because every one of them otherwise surfaces later as an unexplained 401, 403, or "user not found":

- **The API must be running with the `secured` profile** (`GET /v1/heartbeat` must report `secureMode: true`) with its working directory at the repo root, for the `seedSampleData` step. The permit-all build cannot be seeded *at all* — it resolves every request to the default-showcase owner as GUEST, so no `users` row is ever provisioned and the admin API answers 403. This is why the local stack must be `compose.secured.yaml`.
- **Keycloak must be running and reachable**, with the realm imported: accounts are created there and the `users` rows are JIT-provisioned on first login. Remember the realm import only runs into an empty volume — a stale `keycloak_data` can predate the client the script needs.
- **`KEYCLOAK_CLIENT` must exist, be enabled, and have direct access grants on.** The script has no browser, so the password grant is its only way to obtain a token. The dev realm ships `pensieve-test-client` for this. **The production realm deliberately does not** — it has only the confidential `pensieve-web` client with direct access grants off — so a deployment running `keycloak/import-prod/pensieve-realm.json` cannot be seeded by this script unless an operator adds a direct-grant client to that realm by hand. Do not add one to the prod realm import to make this work: a password-grant client permanently weakens a production authorization server, and these fixtures have no business in production anyway.
- **No admin may exist yet** unless it is the account the script provisions. The `uq_users_single_admin` index makes the bootstrap `UPDATE` fail if a different admin (e.g. the claimed default-showcase row from the bootstrap above) already holds the pin, and the script also treats an `UPDATE` that matches *no* row as an error — that means `SQL_CMD` is pointed at a different database than `BASE_URL` is.

The script targets fresh dev databases; it ends by smoke-asserting the full role/showcase matrix (trial import → 403, lapsed filter → 402, second admin → 400, showcase switching, unknown slug → 404), so a seeded environment is also a verified one. Any unexpected response exits non-zero.

One detail worth knowing if you edit it: the accounts it creates in Keycloak are marked `emailVerified: true` on purpose. That is not the (removed) `verifyEmail` login gate — it is what lets the API's claim-by-email path relink an account whose `users` row outlived its Keycloak identity, which is exactly what happens when the Keycloak volume is wiped and the database is not.

## Where to Find the Requirements

- **Design intent** lives in the Javadoc-style comments on the `Entity` and `System` classes (and the `Keychain`).
- **Per-entity requirements** live in that entity's integration test. For example, the rules for a video game box are documented and enforced in `VideoGameBoxTests.java`. When in doubt about expected behavior, read the test.
- **The HTTP contract** is in [`openapi.yaml`](./openapi.yaml); ready-to-run example requests are in [`api.postman_collection.json`](./api.postman_collection.json).
- **The MCP sidecar** documents its tools, env vars, and host setup in [its own repository's README](https://github.com/sethhaskellcondie/the-game-pensieve-mcp#readme).
- **Keycloak** (realm contents, clients, scopes, minting a token by hand) is in [`../keycloak/README.md`](../keycloak/README.md).
- **Notable past issues** are recorded in [`PastIssues.md`](./PastIssues.md).

## Docker Runtime Flow

Development defines **seven** services. There is deliberately no plain `compose.yaml`: the stack has two security postures and the file name always names the one you are starting — `compose.unsecured.yaml` holds the service definitions, and `compose.secured.yaml` `include`s it and overrides only the backend's profiles.

1. **`db`** — the Postgres database.
2. **`flyway`** — runs the database migrations against `db`, then exits.
3. **`backend`** — the API, built from the project `Dockerfile`. In Docker it loads the `docker` profile (`application-docker.properties`), plus `secured` when started from `compose.secured.yaml`.
4. **`keycloak`** — the authorization server (`start-dev --import-realm`, host port 8081), importing `keycloak/import/pensieve-realm.json` on first boot into a `keycloak_data` volume.
5. **`mcp`** — the MCP sidecar, pulled as the published `sethcondie/the-game-pensieve-mcp:latest` image built from its own repo (host port 8090 → `/mcp`).
6. **`frontend`** — the Next.js app (host port 4200).
7. **`mailpit`** — the dev mail catcher Keycloak's password-reset mail is delivered to (web UI on host port 8025).

They are independent enough to bring up piecemeal: `docker compose -f compose.unsecured.yaml up -d db backend` for the API alone, `up -d backend mcp` to add the sidecar, `up -d keycloak` to work on auth. See the README for the exact commands, and [Deployment](#deployment-production-topology) for the production topology, which differs substantially.

### Security Mode in Docker

Three compose files, and the posture is always explicit in the file name:

| | `compose.unsecured.yaml` | `compose.secured.yaml` | `compose.production.yaml` |
| --- | --- | --- | --- |
| Backend profiles | `docker` — permit-all | `docker,secured` | `docker,secured` |
| MCP enforcement | `MCP_AUTH_MODE` unset → `auto`, which sees `secureMode=false` and stays off | `auto` again, but it sees `secureMode=true` and enforces | `MCP_AUTH_MODE=required` |
| Keycloak | present, but nothing requires a token | required for every non-public route | required for every non-public route |
| Published ports | all services | all services | Caddy only (80/443, TLS) |

So the unsecured stack serves the public showcase with no authentication, matching the original single-user behavior — Keycloak still runs so the OAuth flow can be developed and the realm kept honest. The secured stack is the same containers with authentication on, and is what [Seeding Multi-Role Test Data](#seeding-multi-role-test-data) needs.

```bash
docker compose -f compose.unsecured.yaml up -d
docker compose -f compose.secured.yaml   up -d
```

`compose.secured.yaml` is a thin delta — an `include:` of the unsecured file plus the backend's `SPRING_PROFILES_ACTIVE: docker,secured`, its `PENSIEVE_OAUTH2_*` env (the same values `application-secured.properties` defaults to), and a `depends_on` on `keycloak`. Nothing else is duplicated, so the two cannot drift. Both files resolve to the same compose project (the directory name), so switching modes reuses the same containers and volumes and the database survives the switch — mind [what that means for RLS](#switching-the-backend-between-secured-and-unsecured-modes-but-connected-to-the-same-database) when data was written in the other mode. The sidecar needs no change either way: `auto` reads the backend heartbeat and follows it.

## Multiplatform Deployment

The published Docker Hub images are built for both `linux/amd64` and `linux/arm64`.

### One-Time Setup

Create a builder that supports multiplatform builds:

```bash
docker buildx create --name multiplatform --use
docker buildx inspect --bootstrap
```

### Build and Push the Backend Images

1. Build the application jar:

   ```bash
   ./mvnw install -DskipTests
   ```

2. Build and push the API image:

   ```bash
   docker buildx build --platform linux/amd64,linux/arm64 \
     --build-arg JAR_FILE=target/the_game_pensieve_api.jar \
     -t sethcondie/the-game-pensieve-api:latest \
     --push \
     .
   ```

3. Build and push the Flyway migration image (`Dockerfile.flyway`) — **legacy**, no longer referenced by either compose file: the production backend runs Flyway on startup, and the dev stack uses the official `flyway/flyway` image with the migrations bind-mounted. Keep it only if you migrate out-of-band somewhere.

   ```bash
   docker buildx build --platform linux/amd64,linux/arm64 \
     -f Dockerfile.flyway \
     -t sethcondie/the-game-pensieve-flyway:latest \
     --push \
     .
   ```

### Build and Push the MCP Sidecar Image

Build and push **from the sidecar repository** (`the-game-pensieve-mcp`). No jar or prior build step — the Dockerfile compiles the TypeScript:

```bash
docker buildx build --platform linux/amd64,linux/arm64 \
  -t sethcondie/the-game-pensieve-mcp:latest \
  --push \
  .
```

### Front End (React / Next.js)

The front end is a Next.js (React) application (repo: `the-game-pensieve-web-v2`). It runs as a Node server (`next start`) on container port **3000**, not as a static site.

Key points:

- The browser talks to the Next.js server, which proxies calls to the backend through its own Route Handlers (`/api/*`).
- The backend URL is read **server-side only** from `API_BASE_URL`, including the `/v1` prefix (e.g. `http://localhost:8080/v1`). It is required at **runtime**, not build time, so a single image can target any backend. The app throws on startup if `API_BASE_URL` is unset outside development.
- Against a secured backend the Next.js server is a **BFF**: a confidential Keycloak OIDC client (`pensieve-web`) that runs the authorization-code + PKCE login, keeps the tokens in an httpOnly `iron-session` cookie, and attaches the access token to its server-side calls — the browser never holds a token. It needs `SESSION_SECRET`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, and `OIDC_ISSUER` (browser-facing, for the redirect and `id_token` `iss`), plus `OIDC_INTERNAL_ISSUER` when the container reaches Keycloak over the compose network. A static `API_TOKEN` remains as a non-interactive fallback. See the front end's `.env.example`.
- The image is built with `output: "standalone"` (`next.config.ts`) and a multi-stage `Dockerfile`, both present in the front-end repo.

Build and push the front-end image **from the front-end repository**:

```bash
docker buildx build --platform linux/amd64,linux/arm64 \
  -t sethcondie/the-game-pensieve-web:latest \
  --push \
  .
```

### Running the Deployed Stack

Once the images are published, the whole project runs from the production compose file. It needs a `.env` next to it (copy `.env.production.example`) supplying the three domains, the ACME email, and the secrets:

```bash
docker compose -f compose.production.yaml up -d
```

Nothing is published on a host port except Caddy's 80/443 — the app, the MCP endpoint, and Keycloak are reached at `https://${APP_DOMAIN}`, `https://${MCP_DOMAIN}/mcp`, and `https://${AUTH_DOMAIN}`, so DNS must point at the host before first start for the ACME challenge to complete. Services reach each other over the compose network (`API_BASE_URL=http://backend:8080/v1`, JWKS via `http://keycloak:8080/...`).

The **dev** stack is the one that exposes host ports: `docker compose -f compose.unsecured.yaml up -d` (or `-f compose.secured.yaml`) serves the front end on `localhost:4200` (mapped to the Next.js container's port 3000), the API on `8080`, the MCP endpoint on `8090`, Keycloak on `8081`, and Postgres on `5432`.
