# Developer Notes

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
- [Multi-Tenancy and Row-Level Security](#multi-tenancy-and-row-level-security)
- [Database and Migrations](#database-and-migrations)
- [Testing Strategy](#testing-strategy)
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

- `api/` — the web layer: controllers, the controller advice (`ApiControllerAdvice`), the standard response wrapper (`FormattedResponseBody`), and MVC configuration.
- `domain/` — everything else, organized by concern (`entity/`, `customfield/`, `filter/`, `backupimport/`, `metadata/`, `exceptions/`).

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

A few conventions worth knowing:

- **`createNew()` is implemented per service, not in the base class.** Java cannot call `new T()` on a generic type, so each service constructs its own concrete instance. `updateExisting()` and `deleteById()` are shared in `EntityServiceAbstract`.
- **POSTs are idempotent by intent.** Services run a duplication check inside `createNew()` so that repeating a create request returns a `400` rather than silently inserting a duplicate.
- **Soft deletes.** Tables carry a `deleted_at` column; entities expose `isDeleted()`. Deletion sets the timestamp rather than removing the row.
- **Request vs. Response vs. Slim DTOs.** `(Entity)RequestDto` is the input shape; `(Entity)ResponseDto` is the output shape. Several entities also have a `Slim(Entity)` form — a lightweight projection used when an entity is embedded inside another's response (for example, the systems and games nested inside a video game box).
- **Not every entity exposes every CRUD operation through its controller.** All entities implement the full set internally, but a controller may omit endpoints it does not need.

## Entities and Relationships

The system tracks a physical game collection. The current entities are:

- **System** — a gaming platform (e.g. a console). Referenced by games and boxes.
- **Toy** — a collectible toy.
- **VideoGame** — an individual video game title, optionally tied to a `System`.
- **VideoGameBox** — a physical or collection package that contains one or more video games. Flagged `is_physical` and `is_collection`. Linked to video games through the `video_game_to_video_game_box` join table (a box may hold many games).
- **BoardGame** — a board game title.
- **BoardGameBox** — a physical package associated with a board game.

The video game and video game box relationship is the most involved: boxes and games each reference a `System`, and the join table connects them many-to-many.

## Custom Fields

Custom fields let users attach their own metadata to any entity without schema changes. A custom field is defined once (name, type, and which entity key it applies to) and its values are stored separately from the core entity rows. The supported value types mirror the filter types: text, number, boolean, and time. Custom fields may also define a fixed set of selectable options (`CustomFieldOption`).

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

### The System Filter

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
| `test-container` | Integration tests | Testcontainers (`jdbc:tc:postgresql:...`) |
| `import-tests` | Backup/import tests | Testcontainers |
| `filter-tests1`–`filter-tests8` | Filter integration tests, split across profiles | Testcontainers |

The default credentials in local/docker are user `postgres`, password `root`. Override the active profile with `spring.profiles.active`.

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

The application connects to Postgres as a **superuser**, and superusers bypass RLS even with `FORCE`. So the migration (`V1_14`) creates a dedicated, privilege-limited role **`app_rls`** (`NOLOGIN NOSUPERUSER NOBYPASSRLS`) that is granted only DML on the tenant tables — notably **no** access to `users`/`refresh_tokens`. Each request *assumes* this role for the duration of one transaction; because `app_rls` is a non-superuser, the policies actually bind.

### The per-request flow

The boundary is established by `TenantTransactionFilter` in `api/tenant/`, registered to run **after** Spring Security (so the `SecurityContext` is populated). For each tenant-scoped request it:

1. **Resolves the owner** (`OwnerResolver`) — *before* dropping privileges, since this reads `users`: the authenticated user's id (`Bearer` principal → `users.id` by email), or the seeded **showcase owner** for an anonymous request.
2. **Opens a transaction** and, on that connection, runs `SET LOCAL ROLE app_rls` and `set_config('app.current_owner', <id>, true)`. Both are transaction-local, so nothing leaks across pooled connections.
3. **Proceeds the chain inside that transaction.** Because `JdbcTemplate` reuses the thread-bound transactional connection, every repository call and every `@Transactional` service method observes the role + owner, and RLS scopes all of it.

The public auth endpoints (`/v1/auth/**`) and `/v1/heartbeat` are **skipped** — they read/write `users`/`refresh_tokens`, which `app_rls` cannot touch, so they must run with the application's normal privileges.

### Insert stamping and the showcase owner

`owner_id` is never set by application code. Each column has a DEFAULT that stamps it from the session:

```sql
owner_id INTEGER NOT NULL DEFAULT
    COALESCE(NULLIF(current_setting('app.current_owner', true), '')::int, showcase_owner_id())
```

So ordinary inserts are stamped with the current owner, and writes that have no request context (the migration's own backfill, or raw `@JdbcTest` inserts) fall back to the showcase owner. The **showcase owner is identified by a flag, not a hard-coded id**: `users.is_public_showcase` (a partial unique index guarantees exactly one). It is resolved on demand — by the `showcase_owner_id()` SQL function in DEFAULTs, and by `OwnerResolver` (cached) for anonymous requests — so the SERIAL id can differ per environment without anything breaking.

### Interaction with tests

`@JdbcTest` repository tests load only the JDBC slice, so the filter never runs; they connect as the superuser (bypassing RLS) and their inserts are stamped to the showcase owner by the COALESCE fallback — so they needed no changes. The dedicated tenancy tests (`domain/tenant/RowLevelSecurityTests`, `RepositoryRowLevelSecurityTests`, and `controllers/MultiTenancyTests`) instead `SET LOCAL ROLE app_rls` and set `app.current_owner` explicitly to prove isolation holds — including against a raw `SELECT *`.

### Known limitation / future hardening

Because the app logs in as a superuser, isolation depends on *every* tenant DB access happening inside the request transaction that assumed `app_rls`. Any code path that touches tenant tables outside it (a future `@Scheduled`/CLI job) would run as the superuser and bypass RLS, and must demote explicitly. The complete fix is to have the application **log in as a non-superuser role** (with Flyway migrating as the owner); then RLS binds unconditionally and the footgun disappears.

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

`*` GUEST filters the showcase only (RLS scopes anonymous requests to the showcase owner); GUEST writes/backup/import are blocked at Spring Security (401) before capabilities apply.

### Where it is enforced

The role is resolved once per request in `OwnerResolver.resolveOwner()` (via the pure `deriveRole(User)`) — in the **same `users` lookup that resolves the owner id, before the connection drops to `app_rls`** (which has no grant on `users`). It is stashed in `TenantContext` alongside the owner id. `AccessService.can(...)` reads that request-scoped role (never the DB, so it is safe inside the demoted transaction) and the gates live at the semantic chokepoints: `EntityGatewayAbstract` (`getWithFilters` → FILTER/402, writes → WRITE/403) and `BackupImportGateway` (`getBackupData` → BACKUP/403, `importBackupData` → IMPORT/403). Reads-by-id are ungated (RLS already scopes the row). Enforcement is **only active under the `secured` profile** — the default permit-all build reports full access, preserving the single-user behavior.

New accounts are auto-granted a trial on registration: `AuthService.register` stamps `access_until = now + entitlement.trial-days` (default 30, env `ENTITLEMENT_TRIAL_DAYS`) with `subscription_status='trialing'`, so they resolve to **TRIAL**.

### Admin role management

Admins are themselves a role (pinned via `role_override`). The admin API manages roles:

- `GET /v1/admin/users` — list accounts with their effective role, `role_override`, and billing fields.
- `POST /v1/admin/users/{id}/role` — set `role_override` to one of the five roles, or `null` to revert to auto-derivation.

These routes bypass the tenant transaction filter (they read/write `users`, which `app_rls` cannot touch) and authorize the caller as ADMIN inside the controller. **Bootstrap the first admin** with a one-line SQL update (`users` is not under RLS):

```sql
UPDATE users SET role_override = 'ADMIN' WHERE email = 'you@domain.com';
```

Until the Paddle webhook lands, you can also drive the billing fields directly to grant/revoke access (the role re-derives without a pin):

```sql
-- Grant/extend 1 year of paid access:
UPDATE users SET plan = 'paid', subscription_status = 'active',
    access_until = now() + interval '1 year' WHERE email = 'customer@example.com';

-- Revoke (account becomes LAPSED on its next request):
UPDATE users SET subscription_status = 'canceled', access_until = NULL
    WHERE email = 'customer@example.com';
```

Because the role is resolved per request, any of these changes takes effect on the account's very next request — no re-login required.

**Impersonation (deferred):** a future read-only `X-Act-As-Owner: <userId>` header, honored only for ADMIN callers, will let an admin view another user's collection (READ + FILTER only; WRITE/BACKUP/IMPORT → 403). Not yet implemented.

## Database and Migrations

Persistence is PostgreSQL 16, accessed through Spring's JDBC Template (no ORM). Schema changes are managed by **Flyway**; migrations live in `src/main/resources/migrations` and follow the `V{major}_{minor}__Description.sql` naming convention (e.g. `V1_5__CreateVideoGameTables.sql`). `spring.flyway.validateMigrationNaming=true` is on, so misnamed files will fail the build.

Conventions for new migrations:

- Never edit a migration that has already been applied anywhere — add a new one.
- Tables carry `created_at`, `updated_at`, and a nullable `deleted_at` (soft delete).
- Each migration includes commented-out "Undo" statements at the bottom for manual rollback reference.

## Testing Strategy

The project uses a **diamond testing strategy**: a broad layer of integration tests that exercise the stack from the controller down, plus focused unit tests for the parts that need more rigor (notably custom fields and filters).

- Integration tests use **MockMvc** (bundled with Spring Boot) to drive the controllers.
- They run against **Testcontainers** so each run gets an isolated Postgres instance with no cross-contamination between tests. **Docker must be running** for these tests.
- The filter integration tests are split across the `filter-tests1`–`filter-tests8` profiles to spread the container load.

> On some machines not all containers start reliably. If the suite fails for that reason, reduce the load by commenting out the `GetWithFilters...Tests.java` series.

## Where to Find the Requirements

- **Design intent** lives in the Javadoc-style comments on the `Entity` and `System` classes (and the `Keychain`).
- **Per-entity requirements** live in that entity's integration test. For example, the rules for a video game box are documented and enforced in `VideoGameBoxTests.java`. When in doubt about expected behavior, read the test.
- **The HTTP contract** is in [`openapi.yaml`](./openapi.yaml); ready-to-run example requests are in [`api.postman_collection.json`](./api.postman_collection.json).
- **Notable past issues** are recorded in [`PastIssues.md`](./PastIssues.md).

## Docker Runtime Flow

Running the project in Docker spins up three containers:

1. **`db`** — the Postgres database, with a volume for persistent storage.
2. **`flyway`** — runs the database migrations against `db`, then exits.
3. **`backend`** — the API, built from the project `Dockerfile`. In Docker it loads the `docker` profile (`application-docker.properties`).

The production compose file additionally runs the front end. See the README for the exact commands.

### Security Mode in Docker

The Docker deployment runs **unsecured (permit-all)** by design. Both `compose.yaml` and `compose.production.yaml` set `SPRING_PROFILES_ACTIVE: docker` only — they do **not** activate the `secured` profile — so the containerized API serves the public showcase with no authentication, matching the original single-user behavior. (Authentication and the role/capability gates are gated by the `secured` profile; see [Configuration and Profiles](#configuration-and-profiles) and [Roles and Capabilities](#roles-and-capabilities).)

To run the container in secured mode instead, activate both profiles, e.g. `SPRING_PROFILES_ACTIVE: docker,secured`, and override `JWT_SECRET` with a long random value. Leave it as `docker` alone to keep the not-secure deployment.

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

3. Build and push the Flyway migration image:

   ```bash
   docker buildx build --platform linux/amd64,linux/arm64 \
     -f Dockerfile.flyway \
     -t sethcondie/the-game-pensieve-flyway:latest \
     --push \
     .
   ```

### Front End (React / Next.js)

The front end is a Next.js (React) application (repo: `the-game-pensieve-web-v2`). It runs as a Node server (`next start`) on container port **3000**, not as a static site.

Key points:

- The browser talks to the Next.js server, which proxies calls to the backend through its own Route Handlers (`/api/*`).
- The backend URL is read **server-side only** from `API_BASE_URL`, including the `/v1` prefix (e.g. `http://localhost:8080/v1`). It is required at **runtime**, not build time, so a single image can target any backend. The app throws on startup if `API_BASE_URL` is unset outside development. An optional `API_TOKEN` enables Bearer auth — see the front end's `.env.example`.
- The image is built with `output: "standalone"` (`next.config.ts`) and a multi-stage `Dockerfile`, both present in the front-end repo.

Build and push the front-end image **from the front-end repository**:

```bash
docker buildx build --platform linux/amd64,linux/arm64 \
  -t sethcondie/the-game-pensieve-web:latest \
  --push \
  .
```

### Running the Deployed Stack

Once the images are published, the whole project runs from the production compose file:

```bash
docker compose -f compose.production.yaml up
```

The front end is served on `localhost:4200` (host port 4200 maps to the Next.js container's port 3000). The compose files set `API_BASE_URL=http://backend:8080/v1` so the front-end container reaches the backend over the compose network.
