# The Game Pensieve API

In the Harry Potter series, a Pensieve is a basin where wizards store thoughts and memories outside themselves. This project is a pensieve for a video game collection — a backend API for cataloging games, consoles, and the custom details that matter to that collector.

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
| Authentication | Keycloak (OAuth 2.1 / OIDC), the API is a resource server |
| AI integration | MCP sidecar in [`mcp/`](./mcp) — TypeScript / Node, Streamable HTTP |
| Runtime container | Docker |
| Production edge | Caddy (TLS termination and reverse proxy) |

## Quick Start

Clone the repository, then build the jar, and start the development stack:

```bash
./mvnw install -DskipTests
docker compose up -d
```

Once it is running:

| Service | URL |
| --- | --- |
| Front end | http://localhost:4200 |
| API | http://localhost:8080 |
| MCP endpoint | http://localhost:8090/mcp |
| Keycloak | http://localhost:8081 (admin / admin — dev only) |
| PostgreSQL | `localhost:5432` (postgres / root) |

The development stack runs **unsecured**: no authentication, matching the original single-user behavior. See [Security Modes](#security-modes) to turn authentication on, and [Production Deployment](#production-deployment) for the secured, TLS-terminated topology.

A clone is required: the backend image is built from the jar you just produced, and the production compose file additionally mounts the `Caddyfile` and the Keycloak realm import from this repository.

## Running From Source

### Option 1: Run Everything in Docker

Requires [Docker Desktop](https://www.docker.com/products/docker-desktop/) and a local clone. The two [Quick Start](#quick-start) commands launch six services:

| Service | Role | Host port |
| --- | --- | --- |
| `backend` | the API, built from the jar (`./mvnw install -DskipTests` first) | 8080 |
| `db` | PostgreSQL 16 | 5432 |
| `flyway` | runs the migrations against `db`, then exits | — |
| `keycloak` | authorization server; imports the dev realm on first boot | 8081 |
| `mcp` | the MCP sidecar (`/mcp`) | 8090 |
| `frontend` | the Next.js app | 4200 |

Bring up only what you need — `docker compose up -d db backend` for the API alone, `up -d backend mcp` to add the sidecar. Rebuild the jar and re-run `docker compose up -d --build backend` after changing Java code.

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

Authentication is controlled by the `secured` Spring profile — an **overlay** added alongside a datasource profile (`local`, `docker`), never used on its own.

**Unsecured (default)** — every request is permitted, matching the public showcase behavior. This is the default because the active profile is `local`, which does not include `secured`.

```bash
./mvnw spring-boot:run
```

**Secured** — the API becomes a stateless **OAuth 2.0 resource server**: it validates Keycloak RS256 access tokens (signature via JWKS, plus `iss` and `aud`) and enforces a role-based capability matrix. The API itself has no login, registration, or refresh endpoints — Keycloak issues every token, for both the web app and MCP.

Keycloak must be running and reachable at the configured issuer first:

```bash
docker compose up -d keycloak    # imports the dev realm on first boot
```

Then start the API with both profiles. Use whichever form is convenient:

```bash
# Override the active profiles on the command line
./mvnw spring-boot:run -Dspring-boot.run.profiles=local,secured

# Or via an environment variable
SPRING_PROFILES_ACTIVE=local,secured ./mvnw spring-boot:run

# Or when running a built jar
java -jar target/*.jar --spring.profiles.active=local,secured
```

The resource-server settings live in `application-secured.properties` and are env-overridable — `PENSIEVE_OAUTH2_ISSUER`, `PENSIEVE_OAUTH2_JWK_SET_URI`, `PENSIEVE_OAUTH2_AUDIENCE`. The dev defaults match the compose Keycloak, so running from source against it needs no configuration.

In secured mode, only the following endpoints stay public — enough for an anonymous visitor to browse a public showcase. Everything else requires a valid Bearer access token (anonymous requests return `401`):

| Endpoint | Methods |
| --- | --- |
| `/v1/heartbeat` | GET |
| `/v1/{entity}/*` (read a single resource, six entity types) | GET |
| `/v1/{entity}/function/search` (filtered search) | POST |
| `/v1/filters/**` | GET |
| `/v1/function/counts` | GET |
| `/v1/custom_fields`, `/v1/custom_fields/entity/*` | GET |
| `/v1/metadata/` + `ui-settings`, `default_sort_options`, `saved-filters`, `saved-filter-categories` | GET |
| `/v1/showcases` (public showcase directory) | GET |

To exercise the protected endpoints locally, get a token from Keycloak and send it as an `Authorization: Bearer <token>` header:

```bash
curl -s -X POST http://localhost:8081/realms/pensieve/protocol/openid-connect/token \
  -d client_id=pensieve-test-client -d grant_type=password \
  -d username=seth -d password=password -d 'scope=openid' | jq -r .access_token
```

A first authenticated call provisions the caller's `users` row automatically (a 30-day trial), or claims a seeded row when the token's verified email matches it. `GET /v1/auth/me` reports the resulting identity, effective role, and how long the access window lasts. See [`keycloak/README.md`](./keycloak/README.md) for the realm's clients and users, and [`documentation/DevDocumentation.md`](./documentation/DevDocumentation.md) for the role/capability model.

> The dev Keycloak (`admin` / `admin`, HTTP, test users with known passwords) is for local development only. Production uses a separate realm file with none of that surface — see [Production Deployment](#production-deployment).

### Verifying the API

The heartbeat endpoint confirms the API is running and reports which security mode it is in:

```bash
curl http://localhost:8080/v1/heartbeat
# => {"data":{"message":"thump thump","secureMode":false},"errors":null,"roundTripMs":3}
```

`secureMode` is `true` when the `secured` profile is active. The front end and the MCP sidecar both use this to discover the server's posture without a token.

## MCP (AI Assistant Access)

[`mcp/`](./mcp) is a read-only **MCP (Model Context Protocol)** server that lets AI hosts — Claude Desktop, Claude Code, claude.ai connectors — answer natural-language questions about a collection. It is a separate TypeScript process that fulfills every tool call through this REST API, so its reads inherit exactly the same authorization the web app has, never more.

It runs as the `mcp` service in the compose stack (endpoint `http://localhost:8090/mcp`). Register it with a host:

```bash
claude mcp add --transport http pensieve http://localhost:8090/mcp
```

Against an unsecured backend it needs no token. Against a secured one it enforces OAuth: the host discovers Keycloak from the sidecar's protected-resource metadata and runs the standard authorization-code + PKCE flow, and each user sees only their own collection. Tools, environment variables, and host setup are documented in [`mcp/README.md`](./mcp/README.md).

## Production Deployment

Production is defined by [`compose.production.yaml`](./compose.production.yaml), the [`Caddyfile`](./Caddyfile), and [`.env.production.example`](./.env.production.example). It differs from the development stack in several important ways:

- **Caddy is the only public service.** It binds 80/443, terminates TLS via Let's Encrypt, and reverse-proxies three hostnames (app, MCP, auth). Nothing else publishes a host port.
- **The backend runs secured** (`docker,secured`) and migrates itself on startup, so there is no separate Flyway service.
- **Keycloak imports the production realm** (`keycloak/import-prod/`), which has no test users, no public dev client, and no anonymous client registration.
- **Everything is parameterized.** Copy `.env.production.example` to `.env`, fill in the three domains and the secrets, and point DNS at the host before the first start so Caddy can complete the ACME challenge.

```bash
docker compose -f compose.production.yaml up -d
```

The images are published to Docker Hub (`the-game-pensieve-api`, `the-game-pensieve-mcp`, `the-game-pensieve-web`); see `documentation/DevDocumentation.md` for the multiplatform build-and-push commands.

## API Design

The API combines REST and RPC styles. Standard CRUD operations follow REST conventions. RPC-style endpoints are identified by `/function/` in their path.

The most common RPC endpoint is search. A typical REST API exposes "get all" as `GET /{resource}`; here it is `POST /{resource}/function/search`, which accepts an array of filter objects. When no filters are supplied, all resources are returned.

### Filter System

The search endpoints support a filtering system across multiple data types:

- **Text, Number, Boolean, and Time** filters with a range of operators
- **System** filters for video game / video game box relationships
- **Custom Field** filters for user-defined metadata
- **Sort and pagination** controls

See [`documentation/DevDocumentation.md`](./documentation/DevDocumentation.md) for detailed filter documentation and examples.

## Documentation

Additional documentation lives in the [`documentation/`](./documentation) directory:

- `DevDocumentation.md` — the developer documentation: architecture, the entity pattern, filters, authentication, multi-tenancy and row-level security, roles and capabilities, public showcases, the MCP sidecar, and deployment. **Start here.**
- `openapi.yaml` — the OpenAPI specification for the API
- `api.postman_collection.json` — a Postman collection of example requests
- `PastIssues.md` — a record of notable issues encountered during development

Two components document themselves alongside their code:

- [`mcp/README.md`](./mcp/README.md) — the MCP sidecar's tools, configuration, and host setup
- [`keycloak/README.md`](./keycloak/README.md) — the realm contents, clients and scopes, and how to mint a token by hand

## Testing

```bash
./mvnw test          # the Java suite
cd mcp && npm test   # the MCP sidecar's vitest suite
```

The Java suite uses [Testcontainers](https://testcontainers.com/) for integration tests, so **Docker must be running**. The secured-profile suites additionally start a Keycloak container (shared across the whole run) and mint real access tokens against it. On some machines not all containers start successfully; if the test suite fails for this reason, you can reduce the load by commenting out the `GetWithFilters...Tests.java` series of tests.

The sidecar's vitest suite is hermetic — no backend, database, or Keycloak needed — so it runs without Docker.

## License

This project is proprietary. Copyright (c) 2023-2026 Seth Condie. All rights reserved. The source is publicly viewable, but no rights to use, copy, modify, or distribute it are granted without prior written permission. Versions distributed before this change remain available under the MIT License. See [`LICENSE`](./LICENSE) for the full terms.
