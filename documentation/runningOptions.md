# Running Options

Every way this project can be started, what each option includes, and when to reach for it.

The stack has grown three independent axes, and almost every "how do I run this?" question is really a
question about which point on each axis you want:

| Axis | Choices | Chosen by |
| --- | --- | --- |
| **Security posture** | unsecured (permit-all) / secured (OAuth 2.1) | the `secured` Spring profile — i.e. the compose file, or the profiles you pass |
| **Where the code runs** | all in Docker / from source on the host / a hybrid of both | which services you start in compose vs. run yourself |
| **Environment** | development / production / test | the compose file (`*.unsecured`, `*.secured`, `*.production`) or the Maven test profiles |

They combine freely: "secured, backend from source, everything else in Docker" is a normal working setup,
and so is "unsecured, everything in Docker, front end from source." The sections below name the
combinations that are actually useful and say what each one buys you.

A fourth, softer axis is **which services you include**. The dev stack defines seven services and they are
independent enough to start piecemeal — see [Option 3](#option-3--a-subset-of-the-dev-stack).

---

## Quick Chooser

| I want to… | Run this |
| --- | --- |
| Just try the app — no clone, no build | [Option 15](#option-15--the-demo-pull-and-run) — `docker compose -f compose.demo.yaml up -d` |
| See the whole app working, fastest dev path | [Option 1](#option-1--full-dev-stack-in-docker-unsecured) — `docker compose -f compose.unsecured.yaml up -d` |
| Work on auth, roles, multi-tenancy, showcases | [Option 2](#option-2--full-dev-stack-in-docker-secured) — `docker compose -f compose.secured.yaml up -d` |
| Iterate on Java code with a debugger | [Option 4](#option-4--api-from-source-unsecured) / [Option 5](#option-5--api-from-source-secured) |
| Work on the API only, no front end or sidecar | [Option 3](#option-3--a-subset-of-the-dev-stack) — `up -d db backend` |
| Work on the front end | [Option 7](#option-7--front-end-from-source-against-a-docker-backend) |
| Work on the MCP sidecar | [Option 8](#option-8--mcp-sidecar-from-source-against-a-docker-backend) |
| Work on the realm, login pages, or account emails | [Option 11](#option-11--keycloak-and-mailpit-only) |
| Deploy for real | [Option 9](#option-9--production-stack) |
| Run the test suites | [Option 10](#option-10--test-runtimes) |
| Get realistic multi-role data into a running stack | [Option 13](#option-13--seeding-a-running-stack) |
| Just build / lint, or ship (or dry-run) a release | [Option 14](#option-14--build-gate-and-release) |

---

## The Security Posture, Once

This distinction runs through every option below, so it is worth stating once.

Authentication is controlled by the **`secured` Spring profile**, an *overlay* added alongside a datasource
profile (`local`, `docker`, `test-container`, …) and never used alone.

- **Unsecured (permit-all)** — no profile overlay. Every request is anonymous, resolves to the
  **default-showcase owner**, and `AccessService` reports full access. This is the original single-user
  behavior and what the public showcase serves.
- **Secured** — the API becomes a stateless **OAuth 2.0 resource server**: it validates Keycloak RS256
  access tokens (JWKS signature + `iss` + `aud`) and enforces the role/capability matrix. It has no login
  or refresh endpoints of its own; Keycloak issues every token.

Two things that are *not* toggled by the profile, and surprise people:

- **Row-Level Security always runs.** `TenantTransactionFilter` is registered on `@ConditionalOnWebApplication`
  with no profile gate. The profile only changes *which owner id is resolved*, never whether isolation is
  enforced.
- **A public read surface exists in secured mode too** — heartbeat, single-resource GETs, search, filters,
  counts, custom fields, metadata reads, and the showcase directory stay open so an anonymous visitor can
  browse. Everything else answers `401` without a token.

`GET /v1/heartbeat` reports which build is running, and both the front end and the MCP sidecar use it to
discover the posture without a token:

```bash
curl http://localhost:8080/v1/heartbeat
# => {"data":{"message":"thump thump","secureMode":false},"errors":null,"roundTripMs":3}
```

There is deliberately **no plain `compose.yaml`** — the posture is always named on the command line.

---

## Option 1 — Full Dev Stack in Docker, Unsecured

```bash
./mvnw install -DskipTests
docker compose -f compose.unsecured.yaml up -d
```

**What runs** — all seven services: `frontend` (4200), `backend` (8080), `mcp` (8090), `keycloak` (8081),
`mailpit` (8025), `db` (5432), and `flyway` (runs the migrations, then exits).

**What you get**

- The full product end to end with **no authentication**: every request is permitted.
- The MCP sidecar with `MCP_AUTH_MODE` unset → `auto`, which reads `secureMode=false` from the backend
  heartbeat and **stays off**, so hosts connect tokenless.
- Keycloak still runs — so the OAuth flow can be developed and the realm kept honest — but nothing requires it.

**When to use it** — the default. Feature work on entities, filters, custom fields, backup/import, the
front end, or the sidecar's tool surface; demos; anything where a login would only be in the way. It is
also the closest thing to the public showcase deployment.

**Caveats**

- The `backend` image is **built from your local jar** (`build.context: .`, `JAR_FILE: target/*.jar`), so
  `./mvnw install -DskipTests` must run first, and after changing Java code you need
  `docker compose -f compose.unsecured.yaml up -d --build backend`.
- `scripts/seed-test-data.sh` **cannot** seed this stack — see [Option 13](#option-13--seeding-a-running-stack).

---

## Option 2 — Full Dev Stack in Docker, Secured

```bash
./mvnw install -DskipTests
docker compose -f compose.secured.yaml up -d
```

**What runs** — the same seven services. `compose.secured.yaml` `include`s `compose.unsecured.yaml` and
overrides only the backend: `SPRING_PROFILES_ACTIVE: docker,secured`, the three `PENSIEVE_OAUTH2_*` values,
and a `depends_on` on `keycloak`. Nothing else is duplicated, so the two files cannot drift.

**What you get**

- Real token validation (signature via JWKS, plus `iss` and `aud`).
- The **role/capability matrix** enforced (GUEST / TRIAL / PAID / LAPSED / ADMIN), including the `402` and
  `403` paths.
- **Multi-tenant RLS with real owners** — each account sees only its own rows, instead of everything
  collapsing onto the default-showcase owner.
- **Admin impersonation**, **public showcases**, and the JIT provisioning path (`GET /v1/auth/me`).
- The sidecar flips itself on: still `auto`, but it now sees `secureMode=true` and enforces OAuth on `/mcp`,
  advertising Keycloak through its protected-resource metadata.

**When to use it** — anything touching authentication, roles, entitlement/trial logic, tenancy, showcase
switching, or the sidecar's OAuth flow. It is also the **only dev posture that `scripts/seed-test-data.sh`
can seed**.

**Getting a token by hand** (dev realm only — `pensieve-test-client` has direct access grants on):

```bash
curl -s -X POST http://localhost:8081/realms/pensieve/protocol/openid-connect/token \
  -d client_id=pensieve-test-client -d grant_type=password \
  -d username=seth -d password=password -d 'scope=openid' | jq -r .access_token
```

Request only `scope=openid` — `pensieve:read` and `email` are default client scopes and attach
automatically; asking for a default scope by name is rejected as `invalid_scope`.

**Caveats**

- Both compose files resolve to the **same compose project** (the directory name), so switching modes
  reuses the same containers and volumes — the database survives the switch. Read
  [Switching Between Modes](#switching-between-modes) before doing that with real data in the database.
- The dev Keycloak (`admin`/`admin`, HTTP, test users with known passwords) is **development only**.

---

## Option 3 — A Subset of the Dev Stack

Either dev compose file accepts a service list, so you can start only what the task needs:

```bash
docker compose -f compose.unsecured.yaml up -d db            # database only
docker compose -f compose.unsecured.yaml up -d db backend    # the API alone
docker compose -f compose.unsecured.yaml up -d backend mcp   # API + sidecar
docker compose -f compose.unsecured.yaml up -d keycloak      # auth work only
```

**What you get** — a smaller, faster stack and fewer moving parts in the logs. Dependencies still pull in
what they need (`backend` pulls `db`; in the secured file it also pulls `keycloak`).

**When to use it** — API-only work (skip the front end), sidecar work (skip the front end), providing a
database for [Option 4](#option-4--api-from-source-unsecured), or bringing up Keycloak for a from-source
secured run. Also the pragmatic answer on machines where seven containers is a lot.

**Caveats**

- Starting `backend` without `flyway` is fine: the backend runs Flyway on startup itself (flyway-core is on
  the classpath and never disabled). The `flyway` service exists so the schema is also correct for anything
  that touches the database *before* the backend boots.
- `db` in the dev files has **no named volume**. Data survives `stop`/`start` and a mode switch, but a
  `docker compose down` removes the container and the next `up` gives you an empty database.

---

## Option 4 — API from Source, Unsecured

```bash
docker compose -f compose.unsecured.yaml up -d db     # or use a local PostgreSQL 16 install
./mvnw spring-boot:run                                # profile: local (the default)
```

**What runs** — the API on the host at `http://localhost:8080`, against
`jdbc:postgresql://localhost:5432/pensieve-db` (user `postgres`, password `root`). Flyway migrates on
startup.

**What you get** — the fastest edit/run loop, IDE debugging and hot restart, direct access to logs and
breakpoints, with permit-all behavior.

**When to use it** — day-to-day Java work that does not involve auth.

**Caveats**

- Run it from the **repo root**. `POST /v1/function/seedSampleData`, `/seedMyCollection`, `/function/backup`,
  and `/function/importFromFile` read and write `sampleData.json`, `myCollection.json`, and `backup.json`
  **relative to the working directory**. (The Dockerfile copies the first two to `/app`, which is why the
  container is fine.)
- Requires the **Java 25 JDK**.
- If a `backend` container is also running you have a port clash on 8080 — stop one of them.

---

## Option 5 — API from Source, Secured

```bash
docker compose -f compose.unsecured.yaml up -d db keycloak   # Keycloak is identical in both dev files
./mvnw spring-boot:run -Dspring-boot.run.profiles=local,secured
```

Equivalent forms:

```bash
SPRING_PROFILES_ACTIVE=local,secured ./mvnw spring-boot:run
java -jar target/*.jar --spring.profiles.active=local,secured
```

**What runs** — the API on the host as an OAuth2 resource server, validating tokens from the compose
Keycloak on 8081.

**What you get** — everything from [Option 2](#option-2--full-dev-stack-in-docker-secured) (token
validation, capability matrix, real tenancy, impersonation, showcases) with an IDE debugger attached to the
security filter chain, `OwnerResolver`, and `AccessService`.

**Configuration** — the defaults in `application-secured.properties` already match the compose Keycloak, so
usually nothing to set. Each is env-overridable:

| Property | Env var | Dev default |
| --- | --- | --- |
| `pensieve.oauth2.issuer` | `PENSIEVE_OAUTH2_ISSUER` | `http://localhost:8081/realms/pensieve` |
| `pensieve.oauth2.jwk-set-uri` | `PENSIEVE_OAUTH2_JWK_SET_URI` | `http://keycloak:8080/realms/pensieve/protocol/openid-connect/certs` |
| `pensieve.oauth2.audience` | `PENSIEVE_OAUTH2_AUDIENCE` | `http://localhost:8090/mcp` |

The issuer/JWKS split is deliberate: `issuer` is the canonical host-facing URL Keycloak stamps into `iss`,
while the JWKS is fetched over the compose network. **Running on the host, override the JWKS URI** — a
host process cannot resolve `keycloak:8080`:

```bash
PENSIEVE_OAUTH2_JWK_SET_URI=http://localhost:8081/realms/pensieve/protocol/openid-connect/certs \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=local,secured
```

**Caveats** — `secured` is an overlay: pass it *with* `local`, never alone, or there is no datasource. The
audience must match the realm's Audience mapper or every token is rejected.

---

## Option 6 — The Built Jar

```bash
./mvnw install -DskipTests
java -jar target/the_game_pensieve_api.jar                                       # unsecured, local db
java -jar target/the_game_pensieve_api.jar --spring.profiles.active=local,secured  # secured
```

**What you get** — the same artifact the Docker image runs, without Docker in the loop. Useful for
verifying that a change survives packaging, for a quick run on a machine without an IDE, and for sanity
checks before publishing an image.

**Caveats** — same working-directory rule as [Option 4](#option-4--api-from-source-unsecured);
`./mvnw install` runs Checkstyle in the `validate` phase, so lint violations fail the build before the jar
is produced.

---

## Option 7 — Front End from Source Against a Docker Backend

From the `the-game-pensieve-web-v2` clone:

```bash
# in this repo
docker compose -f compose.unsecured.yaml up -d db backend        # or compose.secured.yaml
# in the web repo
npm install
npm run dev        # http://localhost:3000
```

**What you get** — Next.js hot reload against a real API. `.env.development` is preset for exactly this:
`API_BASE_URL=http://localhost:8080/v1`, plus the dev OIDC values (`OIDC_ISSUER=http://localhost:8081/realms/pensieve`,
`pensieve-web` client and its dev secret) so the BFF login flow works when the backend is secured.
`OIDC_INTERNAL_ISSUER` stays unset on the host — it exists only for compose, where the browser-facing and
server-to-server issuers differ.

**When to use it** — all front-end work. Stop the compose `frontend` service (4200) or just ignore it; the
source copy on 3000 is the one you are editing.

**Caveats** — the compose `frontend` is the **published image**, not your working tree; changes never show
up there until the image is rebuilt and pushed from the web repo.

---

## Option 8 — MCP Sidecar from Source Against a Docker Backend

From the `the-game-pensieve-mcp` clone:

```bash
# in this repo
docker compose -f compose.unsecured.yaml up -d db backend        # or compose.secured.yaml
# in the sidecar repo
npm install
API_BASE_URL=http://localhost:8080/v1 PORT=8090 npm run dev      # tsx watch
npm run inspect                                                  # MCP Inspector at http://localhost:8090/mcp
```

**What you get** — sidecar iteration without a Docker rebuild each time. `MCP_AUTH_MODE` defaults to `auto`,
so it follows the backend's heartbeat: off against the unsecured stack, enforcing against the secured one.
Set it to `required` or `disabled` to pin the behavior.

**When to use it** — any sidecar change. To run sidecar changes *inside* compose instead, build the image
from that repo first (`docker build -t sethcondie/the-game-pensieve-mcp:latest .`), since the compose `mcp`
service consumes the published image.

**Register it with a host**

```bash
claude mcp add --transport http pensieve http://localhost:8090/mcp
```

**Caveats** — if you run the sidecar on the host *and* the compose `mcp` service, they fight over 8090.

---

## Option 9 — Production Stack

```bash
cp .env.production.example .env      # then fill it in
docker compose -f compose.production.yaml up -d
```

**What runs** — a different topology, not just a different profile:

- **`caddy`** is the **only** service publishing ports (80/443). It terminates TLS via Let's Encrypt and
  reverse-proxies three hostnames: the app, the MCP sidecar, and auth.
- **`backend`**, **`frontend`**, **`mcp`**, **`keycloak`**, **`db`**, and **`keycloak-db`** are private —
  no host ports, reachable only over the compose network.
- The backend runs `docker,secured` and **migrates itself on startup**, so there is **no `flyway` service**.
- Keycloak has **its own Postgres** (`keycloak-db`), separate from the app database, and imports the
  **production realm** (`keycloak/import-prod/`) — no test users, no `pensieve-test-client`, no anonymous
  DCR, `sslRequired=external`.
- The sidecar runs `MCP_AUTH_MODE=required` — enforce unconditionally, no heartbeat probe.
- All images are the **published** artifacts (`sethcondie/the-game-pensieve-api|-mcp|-web`), pinned to
  the exact versions the release script wrote here in its final step — nothing is built from a local
  jar, and nothing is deployed that did not pass the release gate ([Option 14](#option-14--build-gate-and-release)).
- Named volumes for `postgres_data`, `keycloak_db_data`, `caddy_data`, `caddy_config`.

**When to use it** — real deployment, or a staging host that must behave like one. Not a development
convenience: nothing is reachable without DNS and TLS.

**Preconditions**

- DNS `A`/`AAAA` records for `APP_DOMAIN`, `MCP_DOMAIN`, and `AUTH_DOMAIN` pointing at the host **before**
  first start, so Caddy can complete the ACME challenge.
- A `.env` filled in from `.env.production.example`: the three domains, `ACME_EMAIL`, `SESSION_SECRET`,
  `OIDC_CLIENT_SECRET`, `POSTGRES_PASSWORD`, `KC_DB_PASSWORD`, the Keycloak admin, and the SMTP relay
  settings (Keycloak sends the verification and password-reset mail — there is no Mailpit here).

**After the first deploy, verify** — the realm import runs **once**, on first boot with an empty
`keycloak-db`:

- decode an access token and confirm `aud == https://${MCP_DOMAIN}/mcp` and
  `iss == https://${AUTH_DOMAIN}/realms/pensieve`. A literal `${PENSIEVE_...}` means placeholder
  substitution failed — fix it and re-import. The audience is validated by **both** the sidecar and the
  backend, so a mismatch rejects every token.
- confirm anonymous client registration is rejected
  (`POST /realms/pensieve/clients-registrations/openid-connect`).
- pre-register remote MCP hosts (e.g. claude.ai connectors) as clients in the admin console — production
  has no anonymous DCR on purpose.

**Caveat** — `scripts/seed-test-data.sh` cannot seed production: the prod realm has no direct-grant client,
and that is intentional. Do not add one.

---

## Option 10 — Test Runtimes

```bash
./mvnw test                                # the whole Java suite
./mvnw test -Dtest=SeededDataMatrixTests   # one suite
./mvnw checkstyle:check                    # lint only
```

**What runs** — MockMvc integration tests over **Testcontainers**, so **Docker must be running**. Each
suite family gets its own Testcontainers database via its own Spring profile, which is how they avoid
cross-contamination:

| Profile | Suite |
| --- | --- |
| `test-container` | general controller/integration tests |
| `repository-tests` | entity repository tests (the `afterLoad()` hydration hook) |
| `import-tests` | backup/import tests |
| `rls-tests` | tenancy / row-level-security repository tests |
| `seeded-tests` | the multi-role seed matrix suite |
| `filter-tests1`–`filter-tests8` | filter integration tests, split to spread container load |

**The secured suites** (`*SecuredProfileTests`, `MultiTenancyTests`, `SeededDataMatrixTests`) additionally
start a **real Keycloak container** — a JVM-wide singleton shared across the run — and mint genuine RS256
tokens through `pensieve-test-client`. It mounts **the same realm file the compose stack uses**
(`keycloak/import/pensieve-realm.json`, by host path), so there is no second copy to drift. They run
`{"test-container", "secured"}` (or `{"seeded-tests", "secured"}`), with issuer and JWKS pointed at the
container by `@DynamicPropertySource` while the audience stays the fixed `/mcp` value.

**The MCP sidecar suite** is separate and **hermetic** — `npm test` (vitest) from the sidecar repo, no
backend, database, or Keycloak, so it runs without Docker.

**Caveat** — on some machines not every container starts reliably. If the suite fails for that reason,
reduce the load by commenting out the `GetWithFilters...Tests.java` series.

---

## Option 11 — Keycloak and Mailpit Only

```bash
docker compose -f compose.unsecured.yaml up -d keycloak   # brings mailpit with it
```

**What you get** — the authorization server on `http://localhost:8081` (admin console `admin`/`admin`) with
the dev realm imported, and Mailpit on `http://localhost:8025` catching every message Keycloak sends. The
realm's `smtpServer` points at `mailpit:1025`, so **password-reset and verification mail lands in Mailpit
instead of being delivered** — production is identical except that `smtpServer` is a real relay.

**When to use it** — realm edits, client/scope/mapper changes, login and account theme work, testing the
password-reset flow end to end, or minting tokens for a from-source secured API.

**Caveat** — **`--import-realm` only imports into an empty volume.** Editing
`keycloak/import/pensieve-realm.json` does nothing to a Keycloak that has already booted; a stale
`keycloak_data` volume is the usual reason a realm change or an expected client appears to be missing. Reset
with `docker compose -f compose.unsecured.yaml down -v` and bring it back up.

---

## Option 12 — Migrations on Their Own

The dev `flyway` service migrates `db` and exits, configured by `docker-flyway.config` with the migrations
bind-mounted from `src/main/resources/migrations`:

```bash
docker compose -f compose.unsecured.yaml up flyway
```

**When to use it** — applying a new migration to a running database without restarting the backend, or
checking that a migration applies cleanly on its own. Note that in normal operation you rarely need it: the
backend runs Flyway on startup in **every** option above, which is exactly why production has no `flyway`
service.

**Migration conventions** — `V{major}_{minor}__Description.sql`; `spring.flyway.validateMigrationNaming=true`
is on, so a misnamed file fails the build. Never edit an already-applied migration — add a new one.

---

## Option 13 — Seeding a Running Stack

Not a runtime of its own, but the thing most often needed *after* choosing one.

**Single-owner seed endpoints** — work against any running API, unsecured or secured:

```bash
curl -X POST http://localhost:8080/v1/function/seedSampleData    # sampleData.json
curl -X POST http://localhost:8080/v1/function/seedMyCollection  # myCollection.json
curl -X POST http://localhost:8080/v1/function/backup            # writes backup.json
curl -X POST http://localhost:8080/v1/function/importFromFile    # reads backup.json
```

All four resolve their files **relative to the API's working directory**, so run the API from the repo root
(or use the container, where the Dockerfile copies the data files to `/app`). They populate a **single
owner** only.

**Multi-role seed set** — for exercising every role and the showcase features against realistic data:

```bash
docker compose -f compose.secured.yaml up -d
./scripts/seed-test-data.sh
```

It creates one bootstrap admin, eight users covering TRIAL/PAID/LAPSED, two public showcases, and a
populated default showcase, then smoke-asserts the whole role/showcase matrix — so a seeded environment is
also a verified one. It is rerunnable. Requires `curl` and `jq`, and everything is parameterized
(`BASE_URL`, `KEYCLOAK_URL`, `KEYCLOAK_CLIENT`, `ADMIN_EMAIL`, `SQL_CMD`, …) so it can target a remote dev
or staging host.

**It requires the secured stack.** The permit-all build cannot be seeded at all: it resolves every request
to the default-showcase owner as GUEST, so no `users` row is ever provisioned and the admin API answers
`403`. The target realm also needs a client with **direct access grants** enabled — the dev realm's
`pensieve-test-client`. The production realm deliberately has none.

The integration-test consumer of the same seed set is `./mvnw test -Dtest=SeededDataMatrixTests`
([Option 10](#option-10--test-runtimes)); both run the same choreography over the same seed files in
`src/main/resources/seeders`, so they never drift. **They must never share a database.**

---

## Option 14 — Build, Gate, and Release

Build and lint on their own:

```bash
./mvnw checkstyle:check        # lint (also runs in the validate phase of any build)
./mvnw install -DskipTests     # build the jar without running the suite
./mvnw test                    # suite only
```

**Publishing is no longer done by hand.** One release script drives all three repositories — it is the
only path that pushes images to Docker Hub:

```bash
make release VERSION=1.4.0
# equivalent to: ./scripts/release.sh 1.4.0 ../the-game-pensieve-web-v2 ../the-game-pensieve-mcp
```

The script runs the whole of Pipeline A: preflight (clean trees, free version tag, buildx builder,
docker login) → the three unit suites → local single-arch builds → the **secured** e2e gate → the
**demo** e2e gate → the multiplatform (`linux/amd64` + `linux/arm64`) push of `:X.Y.Z` and `:latest`
with manifest verification → an emulated smoke-run of the architecture the suite didn't execute → a
pin bump of `compose.production.yaml`, commit, and pushed `vX.Y.Z` tag. There is deliberately **no
fast path**: every release pays the full gate. Details in
[`DevDocumentation.md`](./DevDocumentation.md#multiplatform-deployment).

**Dry run — the whole release without publishing anything:**

```bash
PUBLISH=no make release VERSION=1.4.0
```

`PUBLISH=no` is not a fast path — every unit suite, both e2e gates, and both platform builds still run
and still fail the release — it closes the exits instead: nothing is pushed to Docker Hub, no manifest
is verified (there is none), the emulated smoke uses locally built throwaway images, and the pin bump /
commit / tag step is skipped entirely. Git is never touched. Use it to rehearse a release or to prove
the pipeline is green before running it for real.

The two e2e gates can also be run on their own while developing:

```bash
./scripts/e2e-gate.sh secured ../the-game-pensieve-web-v2   # seeded, SECURED_BACKEND=1
./scripts/e2e-gate.sh demo    ../the-game-pensieve-web-v2   # the pull-and-run product
```

Each stands up an isolated throwaway stack (compose project `pensieve-e2e`, remapped ports — your dev
stack and its data are never touched), runs the full Playwright suite against it, and tears it down.

One-time setup for the multiplatform builder (the release preflight checks for it):

```bash
docker buildx create --name multiplatform --use
docker buildx inspect --bootstrap
```

The production compose file consumes the published version pins, so **an image that was not pushed
will not be deployed.**

---

## Option 15 — The Demo, Pull-and-Run

The published product for strangers: every image pulled from Docker Hub, nothing built, nothing cloned.
The compose file is fully self-contained (no bind mounts), so it runs from the file alone:

```bash
curl -fsSLO https://raw.githubusercontent.com/sethhaskellcondie/the-game-pensieve-api/master/compose.demo.yaml
docker compose -f compose.demo.yaml up -d
```

Four services: `frontend` (4200), `backend` (8080), `mcp` (8090), and `db` (deliberately no host port —
nothing outside the stack needs it, and it cannot collide with a local Postgres). No Keycloak, Mailpit,
or Caddy: **the demo is single-user by design.** It runs the permit-all build, so there is no login and
no accounts — every request resolves to the collection's single owner, like the original personal
deployment. Data persists in the named `postgres_data` volume across `down`, restarts, and image updates.

The three images are released together; they default to `:latest` and pin with
`PENSIEVE_TAG=1.4.0 docker compose -f compose.demo.yaml up -d`.

**When to use it** — trying the app on a machine that has only Docker; a personal single-user instance;
the release gate's "Gate B" target (the e2e overlay remaps its ports — see
[Option 14](#option-14--build-gate-and-release)). For development use [Option 1](#option-1--full-dev-stack-in-docker-unsecured)
instead: same posture, but the backend builds from your working tree.

---

## Feature Matrix

| | Opt 15 demo | Opt 1 unsecured Docker | Opt 2 secured Docker | Opt 4 source unsecured | Opt 5 source secured | Opt 9 production | Opt 10 tests |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Backend profiles | `docker` | `docker` | `docker,secured` | `local` | `local,secured` | `docker,secured` | `test-container` (+ `secured`) |
| Token validation | — | — | yes | — | yes | yes | yes (real Keycloak container) |
| Capability matrix enforced | — | — | yes | — | yes | yes | yes (secured suites) |
| RLS running | yes | yes | yes | yes | yes | yes | yes |
| Resolved owner | default showcase | default showcase | per account | default showcase | per account | per account | per test |
| MCP enforcement | `auto` → off | `auto` → off | `auto` → on | n/a (host-run) | n/a (host-run) | `required` | n/a |
| TLS | — | — | — | — | — | Caddy, ACME | — |
| Published ports | all but `db` | all services | all services | 8080 (host) | 8080 (host) | Caddy only | none |
| Keycloak realm | no Keycloak | dev import | dev import | dev import | dev import | prod import | dev import (same file) |
| Seedable by the script | **no** | **no** | yes | no | yes | no (by design) | self-seeding |
| Needs Docker | yes (only Docker) | yes | yes | for `db` only | for `db` + `keycloak` | yes | yes |
| Needs a jar rebuild for Java changes | n/a (published images) | yes (`--build`) | yes (`--build`) | no | no | yes (release) | no |
| IDE debugging | no | awkward | awkward | yes | yes | no | yes |

---

## Ports Reference

| Port | Service | Notes |
| --- | --- | --- |
| 4200 | dev `frontend` | container 3000 (Next.js server) |
| 3000 | front end from source | `npm run dev` in the web repo |
| 8080 | `backend` | `/v1/...`; also the host port for a from-source API |
| 8090 | `mcp` | container 3000; endpoint `/mcp` |
| 8081 | `keycloak` | container 8080; admin console `admin`/`admin` (dev only) |
| 8025 | `mailpit` | web UI; SMTP is 1025 on the compose network only |
| 5432 | `db` | `postgres` / `root`, database `pensieve-db` |
| 80 / 443 | `caddy` (production only) | the only published ports in production |

Watch for clashes: a from-source API on 8080 and the compose `backend`; a from-source sidecar on 8090 and
the compose `mcp`.

---

## Switching Between Modes

Both dev compose files resolve to the **same compose project** (the directory name), so
`up -d` with the other file reuses the same containers and volumes:

```bash
docker compose -f compose.unsecured.yaml up -d     # then, later
docker compose -f compose.secured.yaml   up -d     # same containers, same data
```

The database survives the switch — which is convenient, and has one consequence worth understanding.

**What data each mode can see.** RLS runs identically in both builds; only the resolved owner differs. In
unsecured mode every request resolves to the **default-showcase owner**, so an unsecured instance pointed at
a database that was written in secured mode sees **only** the rows owned by that showcase user. Rows written
by registered accounts carry their own `owner_id` and are invisible and unwritable to it. That is the
intended behavior, not a bug — the database boundary is doing its job.

The one wrinkle: the documented admin bootstrap **claims the default-showcase row**, so anything that admin
writes in secured mode shares the unsecured owner id and *does* show up in unsecured mode (and vice versa).
That is by design — the showcase owner *is* the public collection. If you ever need strict separation,
bootstrap the secured admin as a separate registered account and leave the showcase row unclaimed.

**Volume lifecycle**

| | Survives `stop`/`start` | Survives `down` | Survives `down -v` |
| --- | --- | --- | --- |
| dev `db` (no named volume) | yes | **no** | no |
| dev `keycloak` (`keycloak_data`) | yes | yes | **no** |
| production `postgres_data`, `keycloak_db_data`, `caddy_*` | yes | yes | **no** |

So: wiping the dev database is a `down`; wiping the dev realm — the fix for "my realm edit didn't take" — is
a `down -v`.

---

## Gotchas

- **`./mvnw install -DskipTests` before any Docker run.** The dev `backend` image is built from
  `target/*.jar`; a stale or missing jar is the most common "why is my change not there?"
- **Rebuild the backend image after Java changes** — `up -d --build backend`. `up -d` alone reuses the
  existing image.
- **Run a from-source API from the repo root.** The backup/import/seed endpoints resolve their JSON files
  against the working directory.
- **`--import-realm` only imports into an empty Keycloak volume.** Editing the realm file does nothing to an
  already-booted Keycloak.
- **`secured` is an overlay, never a standalone profile.** `docker,secured` or `local,secured` — alone it
  leaves the app with no datasource.
- **Running secured from the host? Override `PENSIEVE_OAUTH2_JWK_SET_URI`** to `localhost:8081`; the default
  points at `keycloak:8080`, which only resolves inside the compose network.
- **Audience mismatches reject every token**, and both the sidecar and the backend check it independently.
- **The compose `frontend` and `mcp` are published images**, not your working tree. Local changes require a
  rebuild in their own repo, or running them from source ([Options 7](#option-7--front-end-from-source-against-a-docker-backend)
  and [8](#option-8--mcp-sidecar-from-source-against-a-docker-backend)).
- **Tests need Docker** (Testcontainers) — except the sidecar's vitest suite, which is hermetic.

---

## See Also

- [`../README.md`](../README.md) — quick start, security modes, and the endpoint tables
- [`DevDocumentation.md`](./DevDocumentation.md) — profiles, authentication, RLS, roles, seeding,
  deployment, and the multiplatform build commands
- [`../keycloak/README.md`](../keycloak/README.md) — realm contents, clients and scopes, minting a token
- [`../.env.production.example`](../.env.production.example) — every production variable, annotated
- [`PastIssues.md`](./PastIssues.md) — notable problems already hit and solved
