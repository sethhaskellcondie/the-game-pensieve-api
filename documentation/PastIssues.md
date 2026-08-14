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
