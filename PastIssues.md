# Past Issues

## TestContainers fails with "Could not find a valid Docker environment" on Docker Desktop 29+

**Symptom:** All tests fail at startup with `BadRequestException (Status 400)` from every Docker client strategy (TCP, named pipe, Testcontainers Desktop proxy). Docker Desktop is running and `docker ps` works fine.

**Root cause:** TestContainers 1.21.0 hardcodes Docker API version `1.32` as its default when no version is configured. Docker Desktop 29.x dropped support for API versions below `1.40`, so every request to `/v1.32/info` returns HTTP 400.

**Fix:** Add `src/test/resources/docker-java.properties` with the following content:

```properties
api.version=1.41
```

TestContainers shades its own copy of `docker-java-core` and loads this file from the classpath. Setting `api.version` here prevents the `1.32` fallback and makes all Docker API calls use `1.41`, which Docker Desktop 29+ supports.