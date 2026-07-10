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