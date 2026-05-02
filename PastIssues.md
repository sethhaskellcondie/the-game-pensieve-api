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

## 413 Request Entity Too Large on `POST /v1/function/import` (UNRESOLVED)

**Symptom:** Sending a large backup JSON to `POST /v1/function/import` returns HTTP 413.

**Root cause:** The `import` endpoint accepts the full backup payload as a JSON `@RequestBody`. Tomcat's default `maxPostSize` is 2 MB — requests exceeding that are rejected before Spring even processes them. Note: `importFromFile` reads from disk and takes no request body, so it cannot trigger this error.

**Options for fix:**

1. **Raise the limit** — Add to `application.properties`:
   ```properties
   server.tomcat.max-http-form-post-size=-1
   ```
   `-1` removes the size cap entirely. Simplest fix; acceptable for a trusted/internal API.

2. **Use `importFromFile`** — Upload the file to the server first (SCP, shared volume, etc.), then call `POST /v1/function/importFromFile`. No request body involved, so no size limit applies. The infrastructure for this already exists in `BackupImportController`.

3. **Add a multipart file upload endpoint** — New endpoint accepting `MultipartFile`, writes it to disk as `backup.json`, then delegates to `gateway.importBackupData(...)`. Allows clients to stream large files without a monolithic JSON body; Spring handles chunked transfer automatically.

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