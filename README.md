# The Game Pensieve API

In the Harry Potter series, a Pensieve is a basin where wizards store thoughts and memories outside themselves. This project is a pensieve for a video game collection — a backend API for cataloging games, consoles, and the custom details that matter to that collector.

## Related Links

- **Video walkthrough** — a presentation of this project as if delivered in a technical interview: https://youtu.be/7wByiXr5nDI
- **Front end** (React / Next.js): https://github.com/sethhaskellcondie/the-game-pensieve-web-v2
- **MCP sidecar** (TypeScript / Node): https://github.com/sethhaskellcondie/the-game-pensieve-mcp

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
| AI integration | MCP sidecar ([its own repo](https://github.com/sethhaskellcondie/the-game-pensieve-mcp)) — TypeScript / Node, Streamable HTTP |
| Runtime container | Docker |
| Production edge | Caddy (TLS termination and reverse proxy) |

## Try the Demo (No Clone Required)

The fastest way to run The Game Pensieve is the demo: every image is pulled from Docker Hub, so the only requirement is [Docker](https://www.docker.com/products/docker-desktop/). Download [`compose.demo.yaml`](./dockerCompose/compose.demo.yaml) anywhere and run it:

```bash
curl -fsSLO https://raw.githubusercontent.com/sethhaskellcondie/the-game-pensieve-api/master/dockerCompose/compose.demo.yaml
docker compose -f compose.demo.yaml up -d
```

Then open http://localhost:4200. The API is at http://localhost:8080/v1 and the MCP endpoint at http://localhost:8090/mcp.

**The demo is single-user by design.** It runs the unsecured (permit-all) build.  The users, roles, and showcases are not included in the demo.

## Running From Source

### Option 1: Run Everything in Docker

Requires [Docker Desktop](https://www.docker.com/products/docker-desktop/) and a local clone.

| Service | Role | Host port |
| --- | --- | --- |
| `backend` | the API, built from the jar (`./mvnw install -DskipTests` first) | 8080 |
| `db` | PostgreSQL 16 | 5432 |
| `flyway` | runs the migrations against `db`, then exits | — |
| `keycloak` | authorization server; imports the dev realm on first boot | 8081 |
| `mcp` | the MCP sidecar (`/mcp`) | 8090 |
| `frontend` | the Next.js app | 4200 |
| `mailpit` | dev mail catcher; Keycloak's password-reset mail lands here | 8025 |

The compose files live in [`dockerCompose/`](./dockerCompose). Run them from the repo root — every path inside them (the backend's build context, the Keycloak realm import, the Flyway config) is written relative to the compose file itself, so they resolve correctly from any working directory, and each file pins its compose project name rather than inheriting it from a directory.

Bring up only what you need — `docker compose -f dockerCompose/compose.unsecured.yaml up -d db backend` for the API alone, `up -d backend mcp` to add the sidecar. Rebuild the jar and re-run `docker compose -f dockerCompose/compose.unsecured.yaml up -d --build backend` after changing Java code. Swap in `-f dockerCompose/compose.secured.yaml` to run the same services with authentication on.

### Option 2: Run the API Locally

**Requirements**

- The [Java 25 JDK](https://www.oracle.com/java/technologies/downloads/)
- A PostgreSQL 16 database, provided either by:
  - the Docker `db` service (`docker compose -f dockerCompose/compose.unsecured.yaml up db`), or
  - a [local PostgreSQL 16 install](https://www.postgresql.org/download/) (default credentials: user `postgres`, password `root`)

**Steps**

1. Start a PostgreSQL database using one of the options above.
2. Run the application from your preferred IDE, or build and run the jar as described in Option 1.

The API is served on port `8080`.

### Security Modes

Authentication is controlled by the `secured` Spring profile — an **overlay** added alongside a datasource profile (`local`, `docker`), never used on its own.

In Docker the mode is chosen by the docker-compose file — the whole difference between the two is the backend's active profiles, so `compose.secured.yaml` `include`s `compose.unsecured.yaml` and overrides only that. Both pin the same compose project name (`the-game-pensieve-api`), so switching modes reuses the same containers and volumes, and the database survives the switch:

```bash
docker compose -f dockerCompose/compose.unsecured.yaml up -d    # backend profiles: docker
docker compose -f dockerCompose/compose.secured.yaml   up -d    # backend profiles: docker,secured
```

Running from source, the mode is chosen by the profiles you pass.

**Unsecured (default)** — every request is permitted, matching the public showcase behavior. This is the default because the active profile is `local`, which does not include `secured`.

```bash
./mvnw spring-boot:run
```

**Secured** — the API becomes a stateless **OAuth 2.0 resource server**: it validates Keycloak RS256 access tokens (signature via JWKS, plus `iss` and `aud`) and enforces a role-based capability matrix. The API itself has no login, registration, or refresh endpoints — Keycloak issues every token, for both the web app and MCP.

Keycloak must be running and reachable at the configured issuer first:

```bash
docker compose -f dockerCompose/compose.unsecured.yaml up -d keycloak    # imports the dev realm on first boot
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

### Verifying the API

The heartbeat endpoint confirms the API is running and reports which security mode it is in:

```bash
curl http://localhost:8080/v1/heartbeat
# => {"data":{"message":"thump thump","secureMode":false},"errors":null,"roundTripMs":3}
```

## Documentation

Additional documentation lives in the [`documentation/`](./documentation) directory. The production
host — providers, Droplet provisioning, first bringup, backups — has its own runbook:
[`documentation/buildFromScratch.md`](./documentation/buildFromScratch.md).

### Deploying to production

> ⚠️ **Unverified** — written before the production host exists; verified live at launch Stage 11,
> which removes this banner.

```bash
make deploy VERSION=1.0.0     # preflight locally, then deploy that released version to the Droplet
```

Rollback is the same command with the previous version — there is deliberately no separate rollback
script. The deploy prints the rollback command on every failure and after every success; migrations are
forward-only (kept additive), so rolling the image back never rolls the schema back. Rehearse either
direction first with `DRY_RUN=yes ./scripts/deploy-production.sh <version>` — every check runs, nothing
changes. Full detail: [`documentation/scriptExplainer.md`](./documentation/scriptExplainer.md).

## License

This project is proprietary. Copyright (c) 2023-2026 Seth Condie. All rights reserved. The source is publicly viewable, but no rights to use, copy, modify, or distribute it are granted without prior written permission. Versions distributed before this change remain available under the MIT License. See [`LICENSE`](./LICENSE) for the full terms.
