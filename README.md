# The Game Pensieve API

In the Harry Potter series, a Pensieve is a basin where wizards store thoughts and memories outside themselves. This project is a pensieve for a video game collection — a backend API for cataloging games, consoles, and the custom details that matter to a collector.

## Related Links

- **Video walkthrough** — a presentation of this project as if delivered in a technical interview: https://youtu.be/7wByiXr5nDI
- **Front end** (React / Next.js): https://github.com/sethhaskellcondie/the-game-pensieve-web-v2

## Tech Stack

| Concern | Technology |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot |
| Build / package manager | Maven (via the included wrapper) |
| Database | PostgreSQL 16 |
| Database access | JDBC Template |
| Migrations | Flyway |
| Runtime container | Docker |

## Quick Start (No Clone Required)

Published images for the API, database migrations, and front end are available on Docker Hub, so you can run the full stack without cloning this repository.

You need two things:

1. [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running.
2. A copy of [`compose.production.yaml`](./compose.production.yaml) from this repository.

Open a terminal in the directory containing the file and run:

```bash
docker compose -f compose.production.yaml up
```

Docker will pull and start each image. Once the stack is running:

- The front end is available at http://localhost:4200
- The API is available at http://localhost:8080

## Running From Source

### Option 1: Run Everything in Docker

**Requirements**

- [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- A local clone of this repository

**Steps**

1. Build the application jar (Docker builds the backend image from this artifact):

   ```bash
   ./mvnw install -DskipTests
   ```

2. Start the stack:

   ```bash
   docker compose up -d
   ```

This launches three containers: the backend API, the Flyway migrations, and the PostgreSQL database. The API is served on port `8080`.

### Option 2: Run the API Locally

**Requirements**

- The [Java 25 JDK](https://www.oracle.com/java/technologies/downloads/)
- A PostgreSQL 16 database, provided either by:
  - the Docker `db` service (`docker compose up db`), or
  - a [local PostgreSQL 16 install](https://www.postgresql.org/download/) (default credentials: user `postgres`, password `root`)

**Steps**

1. Start a PostgreSQL database using one of the options above.
2. Run the application from your preferred IDE, or build and run the jar as described in Option 1.

The API is served on port `8080`.

### Security Modes

Authentication is controlled by the `secured` Spring profile. The application always runs with the `local` profile (which provides the database connection settings); adding `secured` switches authentication on.

**Unsecured (default)** — every request is permitted, matching the public showcase behavior. This is the default because the active profile is `local`, which does not include `secured`.

```bash
./mvnw spring-boot:run
```

**Secured** — stateless JWT authentication is enforced. Keep the `local` profile (for the database settings) and add `secured`. Use whichever form is convenient:

```bash
# Override the active profiles on the command line
./mvnw spring-boot:run -Dspring-boot.run.profiles=local,secured

# Or via an environment variable
SPRING_PROFILES_ACTIVE=local,secured ./mvnw spring-boot:run

# Or when running a built jar
java -jar target/*.jar --spring.profiles.active=local,secured
```

In secured mode, only the following endpoints stay public; everything else requires a valid Bearer access token (anonymous requests return `401`):

- `GET /v1/heartbeat`
- `POST /v1/auth/register`, `POST /v1/auth/login`, `POST /v1/auth/refresh`
- `GET /v1/{entity}/*` (read a single resource) and `POST /v1/{entity}/function/search` (filtered search) for the six entity types
- `GET /v1/filters/**`

To exercise the protected endpoints locally, register or log in through the auth endpoints to obtain a token, then send it as an `Authorization: Bearer <token>` header.

> The JWT secret falls back to a hardcoded local value for development. Always override `JWT_SECRET` (with a long, random 256-bit value) in any non-local environment.

### Verifying the API

The heartbeat endpoint confirms the API is running:

```bash
curl http://localhost:8080/v1/heartbeat
# => thump thump
```

## API Design

The API combines REST and RPC styles. Standard CRUD operations follow REST conventions. RPC-style endpoints are identified by `/function/` in their path.

The most common RPC endpoint is search. A typical REST API exposes "get all" as `GET /{resource}`; here it is `POST /{resource}/function/search`, which accepts an array of filter objects. When no filters are supplied, all resources are returned.

### Filter System

The search endpoints support a filtering system across multiple data types:

- **Text, Number, Boolean, and Time** filters with a range of operators
- **System** filters for video game / video game box relationships
- **Custom Field** filters for user-defined metadata
- **Sort and pagination** controls

See [`documentation/Notes.md`](./documentation/Notes.md) for detailed filter documentation and examples.

## Documentation

Additional documentation lives in the [`documentation/`](./documentation) directory, including:

- `openapi.yaml` — the OpenAPI specification for the API
- `api.postman_collection.json` — a Postman collection of example requests
- `Notes.md` — filter system documentation and examples
- `PastIssues.md` — a record of notable issues encountered during development

## Testing

This project uses [Testcontainers](https://testcontainers.com/) for integration tests, which requires Docker to be running. On some machines not all containers start successfully; if the test suite fails for this reason, you can reduce the load by commenting out the `GetWithFilters...Tests.java` series of tests.

## License

This project is proprietary. Copyright (c) 2023-2026 Seth Condie. All rights reserved. The source is publicly viewable, but no rights to use, copy, modify, or distribute it are granted without prior written permission. Versions distributed before this change remain available under the MIT License. See [`LICENSE`](./LICENSE) for the full terms.
