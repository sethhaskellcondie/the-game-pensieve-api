# Past Issues

## TestContainers fails with "Could not find a valid Docker environment" on Docker Desktop 29+

**Symptom:** All tests fail at startup with `BadRequestException (Status 400)` from every Docker client strategy (TCP, named pipe, Testcontainers Desktop proxy). Docker Desktop is running and `docker ps` works fine.

**Root cause:** TestContainers 1.21.0 hardcodes Docker API version `1.32` as its default when no version is configured. Docker Desktop 29.x dropped support for API versions below `1.40`, so every request to `/v1.32/info` returns HTTP 400.

**Fix:** Add `src/test/resources/docker-java.properties` with the following content:

```properties
api.version=1.41
```

TestContainers shades its own copy of `docker-java-core` and loads this file from the classpath. Setting `api.version` here prevents the `1.32` fallback and makes all Docker API calls use `1.41`, which Docker Desktop 29+ supports.

---

## Concurrent import requests corrupt data / race condition on import endpoints

**Symptom:** Two simultaneous calls to any import endpoint (`/import`, `/importFromFile`, `/seedSampleData`, `/seedMyCollection`) can interleave, causing duplicate records, ID mapping collisions, or partial imports.

**Root cause:** The import endpoints were stateless — nothing prevented concurrent calls from running `importBackupData` at the same time. Each import builds its own in-memory ID mapping (old ID → new DB ID) and writes to shared tables; concurrent imports corrupt those mappings and produce inconsistent data.

**Fix:** Added an `AtomicBoolean importInProgress` to `BackupImportGateway`. Each import endpoint calls `gateway.tryStartImport()` (`compareAndSet(false, true)`) before doing any work. If it returns `false`, a new `ExceptionImportInProgress` is thrown, which `ApiControllerAdvice` maps to **HTTP 409 Conflict**. A `finally` block calls `gateway.finishImport()` to release the lock even if the import throws.

Key files changed:
- `domain/backupimport/BackupImportGateway.java` — `tryStartImport()` / `finishImport()`
- `domain/exceptions/ExceptionImportInProgress.java` — new exception
- `api/ApiControllerAdvice.java` — handler for `ExceptionImportInProgress` → 409
- `api/controllers/BackupImportController.java` — lock/unlock wrapping all four import endpoints

*(Layout note, 2026-08-14: the endpoints now live in two controllers — `importFromFile` moved to
`LocalFileImportController`, which exists only in unsecured builds. The lock is unchanged and still held by
`BackupImportGateway`, so it covers both controllers; only the file list above is historical.)*

**Scaling consideration:** `AtomicBoolean` is in-memory and scoped to a single JVM instance. If this app is ever horizontally scaled across multiple instances, the lock will not be shared and concurrent imports can still happen across nodes. The fix for that would be a distributed lock (e.g., a database row with `SELECT FOR UPDATE`, or a Redis `SETNX`), but is unnecessary for a single-instance deployment.

---

## Full test suite hangs — Testcontainers Postgres exhausts `max_connections`

**Symptom:** Running the full suite (`./mvnw test`) freezes partway through. The Maven JVM drops to ~2% CPU, no new containers start, and the run never finishes. One Postgres container's log fills with `FATAL: sorry, too many clients already` (observed 1500+ times), and `psql` can no longer connect to it.

**Root cause:** A three-part chain that turns one saturated container into a full-JVM hang:

1. **Each test pool eagerly opens 30 connections.** `application.properties` sets `spring.datasource.hikari.maximumPoolSize=30` globally and never sets `minimum-idle`. When `minimum-idle` is unset, HikariCP defaults it to `maximumPoolSize`, so every pool eagerly opens and permanently holds 30 connections — even when idle.
2. **Many pools target the same container.** Tests use 10 distinct `jdbc:tc:postgresql:///<name>` URLs (`db`, `filter-tests1..8`, `import-tests`), each spinning up its own Postgres container with the default `max_connections=100`. The shared `test-container` DB (`db`) is used by ~16 classes, and because `@JdbcTest`, `@SpringBootTest`, `RANDOM_PORT`, and the `secured` profile each produce a *different* Spring context, Spring's context cache keeps several contexts alive at once — each holding its own 30-connection pool against the *same* container. 4 live contexts × 30 = 120 > 100 → connection rejections.
3. **Testcontainers serializes all connections behind one lock.** `org.testcontainers.jdbc.ContainerDatabaseDriver.connect()` is `synchronized` on a single JVM-wide monitor. When a pool can't fill (because its container is saturated), its `connection-adder` thread spins in Testcontainers' `JdbcDatabaseContainer.createConnection` retry-with-sleep loop **while still holding that global lock**. Every other context's pool and Flyway init then block waiting for the lock, and the entire suite stalls. (Confirmed via thread dump: `HikariPool-N:connection-adder` held the lock in `Thread.sleep`; `main` and other connection-adders were `BLOCKED` waiting for it.)

**Fix:** Cap the connection pool in every test profile. The suite runs single-threaded (default surefire `forkCount=1`, no parallelism), so a large pool is unnecessary. Added to each of the 10 test profile files (`application-test-container.properties`, `application-filter-tests1..8.properties`, `application-import-tests.properties`):

```properties
spring.datasource.hikari.maximum-pool-size=4
spring.datasource.hikari.minimum-idle=1
```

`minimum-idle=1` is the key part — idle cached contexts stop holding 30 connections each, so even several live contexts per container stay far under the 100 ceiling. The production value (`maximumPoolSize=30` in `application.properties`) is left untouched. After the fix the full suite passes (205 tests, 0 failures) with a peak of ~15 connections on any single container.

**Other options considered (not applied):**
- **Fewer containers** — consolidating the 8 separate `filter-tests` DBs onto fewer containers would cut Docker resource pressure and the number of pools. Lower priority once the pool is capped.
- **Lower `connectionTimeout`** — would surface a clear error instead of a silent hang if saturation ever recurs.
- **Raise the container's `max_connections`** — only delays the wall; the pool cap is the real fix.
---

## A real personal collection was published in the Docker image and the public repository

**Symptom:** None — nothing failed. That is what makes it worth recording: this was found by reading the `Dockerfile`, not by anything going wrong.

**What happened:** `myCollection.json` (3.3 MB — a real personal games collection) is `COPY`ed into the API image, and `src/main/resources/full_collection_backup_sept_2025.json` (2.2 MB, a second real collection) sat in `src/main/resources`, so it shipped inside the jar. `sethcondie/the-game-pensieve-api:latest` was pushed **public** to Docker Hub on 2026-06-26 and had been pulled 127 times by the time this was noticed on **2026-08-14**. The repository at `github.com/sethhaskellcondie/the-game-pensieve-api` is public too, and both files are tracked in it, so they are also in the git history.

`POST /v1/function/seedMyCollection` then made that data importable by any caller holding IMPORT — under the role matrix at the time, any PAID or ADMIN account.

**Treat this as disclosed.** Removing the files from `HEAD` would not undo it: the data is in published image layers and in git history, and both are public. A history rewrite would be needed to change that, and it would not reach anything already pulled.

**What was decided (2026-08-14), given that the data is already public:**

- `full_collection_backup_sept_2025.json` was **deleted** — nothing in the codebase referenced it, so it was 2.2 MB of dead weight in every jar.
- `myCollection.json` was **kept**, in the repo and in the image. Removing it would break the seed endpoint everywhere while un-disclosing nothing.
- The seed endpoints were moved off IMPORT onto a new **SEED** capability that only ADMIN holds. This is the part that actually changes: the fixture files are the maintainer's data, so loading them is an operational tool, not something a paying customer should be able to fire into their own collection.

**The general lesson, which is the reason this entry exists:** an image is a distribution channel. `COPY` puts a file on every machine that ever pulls the tag, and there is no recall. Before adding a data file to a `Dockerfile` or to `src/main/resources`, decide whether it is fixture data or real data — and if a file is only used by a developer convenience endpoint, it probably does not belong in the published artifact at all.

---

## Custom field names reached the filter SQL as an interpolated literal

**Symptom:** None in normal use. A custom field created with a name like `zzz' OR pg_sleep(10)--` would execute that fragment as SQL the next time anyone filtered on that field — a stored, second-order injection.

**Root cause:** `FilterService.formatWhereStatements` built the clause that selects which custom field is being filtered on by interpolating the name into a quoted SQL literal: `" AND fields1.name = '" + filter.getField() + "'"`. The name is fully user-controlled at creation time, and the filter denylist (`getBlacklistedWords`) only ever inspected the **operand**, never the field name — so nothing examined it between creation and execution.

**Fix (2026-08-14):** the name is bound as a `?` parameter. Because `formatWhereStatements` and `formatOperands` are separate methods walking the same list, they now share an ordering contract: a custom field filter contributes **two** placeholders (name, then value), and a custom field *sort* contributes only the name — its `ORDER BY` is deferred to the end of the statement and carries no placeholder. `FilterServiceCustomFieldNameBindingTests` pins that ordering down; getting it wrong shifts every operand by one and returns wrong rows rather than raising an error, so it is worth the tests.

A character allowlist on the name (`CustomField.isValidName`, enforced at creation and rename) was added as defense in depth. It is *not* the fix — the parameter binding is.

**A trap worth remembering:** the obvious-looking cleanup here is wrong. The query already joins `custom_fields` on `values<i>.custom_field_id = fields<i>.id`, which makes the name clause *look* redundant — but that join places no constraint on **which** custom field, so the name clause is the only thing narrowing the query to the field being filtered on. Deleting it instead of binding it would make every custom field filter silently match values belonging to every custom field.

---

## A showcase visitor could read the owner's *other* metadata by holding any account

**Symptom:** None visible. `GET /v1/metadata/{key}` and the list-all `GET /v1/metadata`, sent with
`X-Showcase: <someone's slug>` and **any valid token**, returned the showcase owner's saved filters, sort
preferences, and whatever else they had stored — not just the four keys a showcase renders from.

**Root cause:** the enumeration guard was an *anonymous-only* URL allowlist. `SecurityConfig`'s
`PUBLIC_METADATA_READ` opens exactly four metadata routes to callers with no token, and it was doing double
duty as the policy. But the security chain's job ends at "may this caller reach this URL": every metadata
route also matches `.anyRequest().authenticated()`, so an authenticated caller passed the chain on any key,
and `X-Showcase` then scoped RLS into the owner's tenant. Registering an account was the entire cost of the
bypass. The list-all route was worse: it is not in the anonymous allowlist at all, which made it *look*
protected, while in fact it was reachable by anyone with a token and returned the owner's whole table.

**Fix (2026-08-14):** the allowlist moved into `MetadataGateway`, keyed on `access.isShowcaseView()` rather
than on whether a token was presented — `ShowcaseMetadata.SHOWCASE_READABLE_KEYS`. `getByKey` throws
`ExceptionForbidden` (403) for anything outside it; `getAllMetadata` narrows the list instead of refusing,
because a 403 on a list endpoint would itself confirm that the owner has other keys. 403 rather than 404 on
the single-key read for the same reason: the key may well exist, and 404 would still separate existing keys
from non-existent ones while being a lie.

**The general lesson:** a URL allowlist on the security chain answers "who may reach this route", never
"what may this request see". Any rule that depends on *request context* — a header, a tenant, a view mode —
has to be enforced where that context lives. The two lists must now be kept in step, and both say so.
`SecurityConfig.PUBLIC_METADATA_READ` and `ShowcaseMetadata.SHOWCASE_READABLE_KEYS` are the same four keys
for different questions.

---

## Health-checking the Keycloak container: no curl, no wget, only bash

**Symptom:** Every obvious `healthcheck:` for the `keycloak` service fails instantly with
`exec: "curl": executable file not found`.

**Root cause:** the official Keycloak image is built on `ubi9-micro`. It ships `/bin/bash` (`kc.sh` needs
it) and essentially nothing else — no curl, no wget, no busybox applets. There is no HTTP client in the
container to probe with.

**Fix (2026-08-14):** use bash's `/dev/tcp` pseudo-device, which opens a socket without any external
binary, and set `KC_HEALTH_ENABLED=true` — health endpoints are off by default and, since Keycloak 25, live
on the **management** interface (port 9000), not the HTTP port:

```yaml
test:
  - CMD
  - bash
  - -c
  - "exec 3<>/dev/tcp/127.0.0.1/9000; printf 'GET /health/ready HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3; grep -q '200 OK' <&3"
```

Two details that are easy to get wrong:

- **Match the HTTP status line, not the body.** The obvious `grep -q '"UP"'` is wrong: `/health/ready`
  answers **503** while a sub-check is down, but its JSON body still contains `"status": "UP"` for every
  sub-check that *is* up. A body grep therefore reports a degraded Keycloak as healthy.
- **Double the backslashes in the YAML.** `\r\n` in a double-quoted YAML scalar becomes a literal control
  character in the file; `\\r\\n` passes the two-character escape through for `printf` to expand. Both
  work, but only the second leaves a config file people can diff and edit.

Same class of problem, different answer, for the backend: `eclipse-temurin` has no HTTP client either, so
the API `Dockerfile` installs `curl` outright. That is worth the ~5 MB for a second reason — the backend
publishes no host port in production, so `docker compose exec backend curl localhost:8080/v1/heartbeat` is
the only way to interrogate it directly.

---

## `chown -R` in a Dockerfile silently duplicates every file it touches

**Symptom:** Adding a non-root `USER` to the API image grew it by 33 MB — almost exactly the size of the
jar and the two JSON data files that were already in it.

**Root cause:** `RUN chown -R app:app /app` after the `COPY`s. A `RUN` creates a layer containing every
file it modifies, and changing ownership modifies every file — so the whole of `/app` was stored twice.

**Fix (2026-08-14):** `COPY --chown=pensieve:pensieve …` on each copy instead, leaving only a tiny `RUN` to
create the log directory. Same result, 60 MB smaller image.

**Related, and the reason that log directory is created in the image at all:** when an empty *named volume*
is mounted over a path that already exists in the image, Docker seeds the volume with that directory's
contents **and its ownership**. Create the directory only in the compose file and the volume arrives
root-owned — which a non-root process cannot write to, so the app boots fine and silently logs nothing.

---

## Login was impossible behind the reverse proxy — the BFF derived its origin from its bind address

**Symptom:** Found by the **first run of `scripts/prod-rehearsal.sh`** (2026-08-13). `GET
https://pensieve.localhost/api/auth/login` redirected to Keycloak with
`redirect_uri=https://0.0.0.0:3000/api/auth/callback`, which Keycloak refused with `HTTP 400 "Invalid
parameter: redirect_uri"`. No login was possible in the production topology at all.

**Root cause:** the frontend container runs the Next.js standalone server with `HOSTNAME=0.0.0.0
PORT=3000`, and `new URL(request.url).origin` inside a Route Handler resolves to the *bind* address, not
the proxied `Host` (the scheme was already right — `X-Forwarded-Proto` is honoured, the host is not).
**Why no other gate could see it:** both e2e passes reach the frontend directly, so the request origin is
always correct there; Caddy exists only in `dockerCompose/compose.production.yaml`, which had never been
run anywhere before the rehearsal.

**Fix (2026-08-13, web repo):** `src/lib/appOrigin.ts` returns `APP_ORIGIN` when set and falls back to the
request's own origin; all three call sites (login, callback, logout) use it, and
`dockerCompose/compose.production.yaml` wires `APP_ORIGIN: https://${APP_DOMAIN}` on the `frontend`
service — **required behind the proxy, and the one piece of frontend configuration with no safe default**.
Configuration rather than trusting `X-Forwarded-Host`, deliberately: the callback redirects the browser to
`new URL(dest, origin)`, so an origin taken from a spoofable header is an open redirect. Full write-up:
`future-plan-implement-pipeline-b.md` §4.9.

**The general lesson:** this is the class of defect only a full-topology rehearsal can catch, and it is why
a green `prod-rehearsal.sh` gates every publish. The Stage 4 re-run after the Stage 2–3 hardening
(2026-08-14) passed all checks first try, including the real-relay SMTP send — but the test message
**landed in the Yahoo recipient's spam folder** despite Resend showing green "delivered" and SPF/DKIM
being verified. That is the exact gap the script warns about: "delivered" means the receiving server
accepted the message, not that it reached the inbox. Expected for a brand-new sending domain with no
reputation and DMARC at `p=none`; the recipient marked it not-spam, which trains the filter. Worth
rechecking after real traffic exists — a password-reset link in spam is a locked-out user, since
self-service reset is the only credential-recovery path.

---

## The release smoke stack broke when the default DB password was removed

**Symptom:** The first `1.0.0` release dry run (`PUBLISH=no ./scripts/release.sh …`) failed at step 7,
the emulated-arch smoke, after every unit and e2e gate had passed. The backend smoke container looped on
`PSQLException: The server requested SCRAM-based authentication, but no password was provided` until the
300s heartbeat wait timed out.

**Root cause:** The pre-deploy hardening pass removed the committed default
`spring.datasource.password=root` from `application-docker.properties`, moving the dev credential into
`compose.unsecured.yaml` and `compose.demo.yaml` — deliberately, so a `docker`-profile run with no
password fails loudly. The sweep covered every *compose* consumer, but step 7 of `release.sh` builds its
throwaway stack with raw `docker run` commands, and it had been silently depending on that committed
default all along. The e2e gates (steps 4–5) kept passing because they run the compose files.

**Fix:** `release.sh` now passes `-e SPRING_DATASOURCE_PASSWORD=root` to the smoke API container, with a
comment explaining why the stack supplies its own dev credential. The smoke db image was also aligned to
`postgres:16.15-alpine` while in there (it still said 16.2 from before the base-image bump).

**Lesson:** when removing a fallback credential, grep for every consumer of the *profile*, not just the
compose files — `docker run` invocations in scripts carry their own env and don't inherit the sweep.

---

## The frontend container crash-looped in every dev/demo stack, and no gate noticed

**Symptom:** The second `1.0.0` release dry run failed at step 7 again — one service further along. The
backend was healthy, but the smoke wait on the frontend's `/api/heartbeat` timed out. The container had
exited immediately with `Refusing to start: SESSION_SECRET is required in production…` and was gone by the
time logs were collected.

**Root cause, in two layers:**

1. The web image bakes `NODE_ENV=production` (it runs a `next build` standalone server), and the B4
   hardening made the BFF exit 1 at boot when `SESSION_SECRET` is missing or under 32 characters. Only
   `compose.production.yaml` set the variable — so the frontend service in `compose.unsecured.yaml`,
   `compose.secured.yaml` (by include), and `compose.demo.yaml` had been crash-looping since B4 landed,
   along with the raw `docker run` web container in `release.sh`'s step-7 smoke.
2. Nothing caught it earlier because **no gate ever exercised the containerized frontend.** The Playwright
   suite drives its own `next dev` server on port 3000 (`NODE_ENV=development`, where the dev-secret
   fallback is allowed), and `e2e-gate.sh` waited only on Postgres, the backend, and Keycloak. Both e2e
   gates passed with a dead frontend container in the stack — the release smoke was the first check in the
   entire pipeline that boots the web *image* and asks it a question.

**Fix:** committed dev-grade `SESSION_SECRET` values on the frontend service in `compose.unsecured.yaml`
and `compose.demo.yaml` (a public dev credential is correct there, exactly like the dev db password — the
guard exists for production, which requires a real value via `:?required`); a throwaway secret in
`release.sh`'s smoke `docker run`; and two new readiness waits in `e2e-gate.sh` — the frontend's
`/api/auth/session` (the image's own healthcheck route) and the mcp sidecar's `/healthz` — so a
crash-looping image now fails the gate in minutes instead of surfacing at the step-7 smoke, 25 minutes in.

**Lesson:** a service that is in the stack but exercised by nothing is worse than absent — it *looks*
covered. When a boot-time guard is added to a service, every stack that runs the image needs the variable,
and at least one gate must actually talk to the container the users will run.

## Rolling back to v1.0.0 deleted the deploy script out from under itself

**Symptom:** The deliberate rollback test (2026-08-17, first thing proven after `1.0.1` went live) died
instantly: `bash: scripts/deploy-production-remote.sh: No such file or directory`, exit 127, before any
remote step ran. Worse than the error itself: the bootstrap's `git checkout v1.0.0` **had already
happened**, so the Droplet was left with the old Caddyfile and compose file on disk while the 1.0.1
stack kept serving — a state where any container restart would pick up the wrong config.

**Root cause:** The deploy scripts were committed *after* `v1.0.0` was tagged. The wrapper's bootstrap
deliberately checks out the target tag first and only then execs the remote script *from that tag* (so
script, compose file, Caddyfile, and realm import always move together) — but for a tag that predates
the scripts, the checkout deletes the very file the next command execs. A second, smaller trap surfaced
while recovering: the remote script resolves the repo root from **its own path** (`SCRIPT_DIR/..`), so
running a copy from `/tmp` fails its asserts — a copied script must sit at `/opt/pensieve/scripts/`.

**Fix:** The rollback was completed by copying the current remote script into the v1.0.0 checkout's
`scripts/` directory and running it from `/opt/pensieve` (then deleting the untracked copy so the next
checkout wouldn't refuse to overwrite it). Permanently: `deploy-production.sh` gained a preflight that
refuses, in about a second and with the reason named, any tag that does not contain
`scripts/deploy-production-remote.sh`. `v1.0.0` is hand-deploy-only forever; every later tag carries
the scripts.

**Lesson:** "the script rides the tag it deploys" is a great coupling right up until you point it at a
tag from before the script existed. Any self-referential deploy design has a boundary at its own birth —
find it with a deliberate test while nothing is at stake, not during a 2am emergency rollback.

## The rollback "reverted" the Caddyfile on disk but Caddy kept serving the new config

**Symptom:** After the successful rollback to `1.0.0`, the auth host still served 1.0.1's framing
behavior (Keycloak's own `frame-ancestors 'self'`, no Caddy-injected `'none'`) even though v1.0.0's
Caddyfile — which *does* inject `frame-ancestors 'none'` on every host — was checked out on disk. The
rollback's step 6 showed `pensieve-caddy-1 Running`, not `Recreated`.

**Root cause:** The Caddyfile is a read-only bind mount, and compose only recreates a container when
its image or service definition changes — **file content behind a bind mount is invisible to it.**
Caddy loads its config once at start and keeps it in memory. Benign in this direction (the fixed config
stayed live); the dangerous direction is a forward deploy whose only change is the Caddyfile: `up -d`
would restart nothing and the change would silently never land, while step 7's health checks — served
happily by the stale config — wave it through.

**Fix:** step 6 of `deploy-production-remote.sh` now follows `up -d` with a graceful
`caddy reload --config /etc/caddy/Caddyfile` (zero downtime, a no-op if caddy was just recreated,
retried briefly while caddy boots, and a hard deploy failure if the config is refused). In the tag
being deployed from 1.0.2 onward.

> **Update 2026-08-18: the reload fix was itself insufficient** — it reloads a *stale* copy of the
> file and reports success. See the next entry ("`caddy reload` reloaded the old Caddyfile...");
> the real fix is an unconditional force-recreate of caddy in step 6.

**Lesson:** `up -d` converges containers, not configuration. Every bind-mounted config file needs an
explicit reload/restart story in the deploy path, or changes to it only apply by coincidence — whenever
something *else* happens to recreate the container.

## The admin console's basic-auth popup could never be satisfied — one Authorization header, two auths

**Symptom:** With 1.0.1's framing fix live, the Keycloak admin console finally got past its iframe
check, through the Caddy gate popup, through Keycloak's own login — and then presented a *third*
credential prompt that no password on earth could satisfy. Entering the gate credentials just
re-prompted, forever. DevTools told the story: `GET /admin/serverinfo` → **401**,
`WWW-Authenticate: Basic realm="restricted"`, `Server: Caddy` — with the console dead behind an
"Unable to determine error message" banner.

**Root cause:** The Caddy gate matched all of `/admin/*`, which includes the **Admin REST API** the
console SPA drives itself with. Those calls carry `Authorization: Bearer <admin token>` — and a
request has exactly **one** `Authorization` header. The two schemes are mutually exclusive on the same
route: send Bearer and Caddy's `basic_auth` rejects the missing Basic; answer the popup and the
browser retries with Basic, which sails through Caddy only for Keycloak to reject the missing Bearer.
An unwinnable loop, by construction. The Caddyfile's own comment claimed "the browser replays the
credentials automatically … the whole console works from one login" — written before anyone could
reach this code path, because at 1.0.0 the frame-ancestors defect blocked the console *earlier* in the
same flow. Two defects, same feature, peeled in order.

**Fix:** Narrow the matcher to what never carries an Authorization header of its own — the console
pages (`/admin`, `/admin/master/console/*`) and the master realm (`/realms/master/*`) — and leave the
Admin REST API (`/admin/realms/*`, `/admin/serverinfo`, …) to Keycloak's own bearer enforcement, which
refuses every unauthenticated call anyway. What the narrower gate gives up: basic-auth shielding of
the API routes against pre-auth CVEs. What it keeps: the gate on the admin *login* surface, where
password guessing would actually happen. `prod-rehearsal.sh` now asserts the API's 401 is **not** a
Basic challenge, so re-widening the matcher fails the rehearsal. Scripted-admin ergonomics improved as
a side effect: REST calls need only the bearer token; only *minting* the token (master realm, no
Authorization header of its own) still crosses the gate with `-u`.

**Lesson:** basic auth at the edge and bearer auth at the service compose only on routes that never
send their own `Authorization` header — the header is a singleton, and any route where both schemes
claim it is not "double-protected", it is *unreachable*. When layering an edge gate over an API,
walk the routes by what credential each request actually carries, not by path prefix.

## `caddy reload` reloaded the old Caddyfile — a single-file bind mount pins the inode

**Symptom:** The 1.0.2 deploy — carrying the admin-gate matcher fix — ran green end to end: checkout
at the tag, health checks passed, deploy log written, and step 6's new `caddy reload` reported
success. Yet the edge kept serving the **old** gate config: `/admin/serverinfo` still answered
`WWW-Authenticate: Basic realm="restricted"`, and the admin console hit the same unpassable popup the
release was meant to fix. `docker ps` gave the tell: every service freshly recreated except
`pensieve-caddy-1 … Up 19 hours`.

**Root cause:** The Caddyfile is a **single-file** bind mount (`../Caddyfile:/etc/caddy/Caddyfile`).
Docker binds a single file by *inode* at container start. `git checkout` (like most tools) replaces
files by writing a new one and renaming it over the old path — a **new inode**. From that moment the
host path and the container path are two different files: the host had the 1.0.2 Caddyfile
(inode 825015), the container still saw 1.0.1's (inode 819885, verified side by side). So
`caddy reload --config /etc/caddy/Caddyfile` inside the container did exactly what it was told —
re-parsed the stale file, loaded the config it was already running, and exited 0. The deploy's
success signal was truthful about the reload and wrong about the outcome.

**Fix:** step 6 now runs `docker compose up -d --force-recreate caddy` unconditionally after the main
`up -d`. A recreate rebinds the mount path, picking up the current inode. ~2–3s of extra edge
downtime inside a step that already accepts 10–60s; TLS certificates are untouched (they live in the
`caddy_data` volume, not the container). The one-off remediation on the box was the same command by
hand. Note the rehearsal could never catch this class of bug: it always boots a fresh stack, where
container creation happens *after* checkout and the mount is never stale — this failure mode only
exists on a host whose containers outlive a `git checkout`.

**Lesson:** a single-file bind mount is a snapshot, not a window — after any rename-style replacement
the container is reading a file that no longer has a name on the host. For config that must track a
git checkout, either mount the *directory* or recreate the container; never trust an in-place reload.
And a deploy step that "verifies" its own mechanism (reload exited 0) is weaker than one that
verifies the *outcome* (the served config changed) — the second kind is what step 7 exists for, and
this bug lived precisely in the gap between them.

## Mid-session gate popups in the admin console — background fetches don't replay basic auth

**Symptom:** With the narrowed 1.0.2 gate live (and the stale caddy mount recreated), the admin
console finally loaded — but navigating it kept throwing the Caddy basic-auth popup at random
moments, and the popup rejected the correct gate password. Canceling it sometimes worked (the console
carried on) and sometimes **logged the admin out entirely**.

**Root cause:** The matcher still covered all of `/realms/master/*`, and the console continuously
talks to that realm in the background: token refresh (`protocol/openid-connect/token`), the
session-status iframe, the 3p-cookie check pages. Those are `fetch()` calls and iframes — and
browsers only replay cached basic-auth credentials *reliably* on **top-level navigations**. When
several background requests 401 at once, the browser queues one credential prompt per request, so
entering the (correct) password on one prompt is immediately followed by the next — indistinguishable
from a rejection. Canceling the prompt attached to a session-status poll was harmless; canceling the
one attached to the token refresh killed the session. Which one you got was luck.

**Fix:** The gate now covers **exactly the interactive login surface** — the console shell (`/admin`,
`/admin/`, `/admin/master/console/*`), the master login page (`/realms/master/protocol/openid-connect/auth`),
and the form target (`/realms/master/login-actions/*`). All top-level navigations; one prompt at the
front door, then the Keycloak login, then silence. Everything programmatic under `/realms/master/*`
is open and protected by Keycloak's own auth. The deliberate trade: the master token endpoint accepts
direct password grants (`admin-cli`) unauthenticated-by-Caddy, so **brute-force detection must be ON
in the master realm** (Realm settings → Security defenses) — the same protection the pensieve realm's
import bakes. Direct grants on `admin-cli` cannot be disabled as a further hardening: `kcadm`
authenticates through them. `prod-rehearsal.sh` pins the shape from both directions (26 checks now):
login page gated, realm metadata and token endpoint answering with Keycloak's own auth, never a
Basic challenge.

**Lesson:** basic auth composes with a browser app only on top-level navigations. The mental model
"the browser replays credentials" is true for pages and false-enough for fetches and iframes that any
gated path a SPA touches in the background will eventually prompt — and the prompt will look broken,
because it queues per-request. Gate the front door, not the hallways.
