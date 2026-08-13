# Docker Compose Explainer

Everything in `dockerCompose/` is one of four topologies — **development**, **demo**, **e2e gate**, and
**production** — plus two overlay files and two environment files. There is deliberately **no plain
`compose.yaml`**: the security posture is always named on the command line, so nobody starts the wrong stack
by habit.

**Paths inside these files are relative to `dockerCompose/`, not to your shell.** Compose resolves build
contexts and bind mounts against the *project directory* — the directory holding the first `-f` file — so
`..` always means the repo root regardless of where you invoke compose from.

| File | Topology | Auth | Images | Published ports |
|---|---|---|---|---|
| `compose.unsecured.yaml` | development | permit-all | backend built locally, rest pulled | 4200, 8080, 8090, 5432, 8081, 8025 |
| `compose.secured.yaml` | development | OAuth2 enforced | same (includes the file above) | same |
| `compose.demo.yaml` | demo / pull-and-run | permit-all, single-user | all pulled | 4200, 8080, 8090 |
| `compose.e2e.yaml` | overlay | — | — | remaps to the 1xxxx range |
| `compose.e2e.secured.yaml` | overlay | — | — | remaps Keycloak + Mailpit |
| `compose.production.yaml` | production | OAuth2 enforced | all pulled | 80, 443 (Caddy only) |

---

## `compose.unsecured.yaml` — development, permit-all

The everyday local stack. The backend runs the `docker` profile alone, which is the permit-all build: no
request needs a token and the API behaves like the original single-user deployment. Keycloak still runs so the
OAuth flow can be developed and the realm kept honest, but nothing requires it.

```
docker compose -f dockerCompose/compose.unsecured.yaml up -d
```

Services: `frontend` (4200), `backend` (8080, **built from the repo** — `target/*.jar` must exist), `mcp`
(8090), `flyway` (migration runner), `db` (5432), `keycloak` (8081), `mailpit` (8025 web UI, catches
Keycloak's verification and password-reset mail).

Notes worth knowing:

- The project name is **pinned** to `the-game-pensieve-api` rather than derived from the directory. Without
  the pin, moving these files into `dockerCompose/` would have stranded the existing dev volumes.
- `KC_HOSTNAME` pins the canonical issuer to `http://localhost:8081` while
  `KC_HOSTNAME_BACKCHANNEL_DYNAMIC` lets in-network containers still fetch JWKS at `http://keycloak:8080`.
- The MCP sidecar's `MCP_AUTH_MODE` is unset (= `auto`), so it sees `secureMode=false` on the backend
  heartbeat and keeps OAuth enforcement off here.

## `compose.secured.yaml` — development, OAuth2 enforced

The same stack with the backend switched to the resource-server build (`SPRING_PROFILES_ACTIVE:
docker,secured`). Use it when you need real authentication locally: the role/capability matrix, multi-tenant
RLS, admin impersonation, showcase grants — and it is **required by `scripts/seed-test-data.sh`**, which
drives real accounts and roles.

```
docker compose -f dockerCompose/compose.secured.yaml up -d
```

It `include`s `compose.unsecured.yaml` and overrides only what changes, so the two cannot drift — every
service definition, port, and volume comes from that file. Both pin the **same project name**, so switching
modes reuses the same containers and volumes: **the database survives a mode switch.**

The three OAuth2 properties (`PENSIEVE_OAUTH2_ISSUER`, `_JWK_SET_URI`, `_AUDIENCE`) are restated here even
though they match `application-secured.properties`, because they are the settings most likely to need
changing and production sets the same three as HTTPS URLs. The audience must match the realm's Audience
mapper or every token is rejected. The sidecar needs no change — on `auto` it detects `secureMode=true` and
starts enforcing by itself.

## `compose.demo.yaml` — the pull-and-run public demo

Self-contained: no bind mounts, no build, no other files needed. Download it anywhere and run it.

```
docker compose -f compose.demo.yaml up -d          # then open http://localhost:4200
PENSIEVE_TAG=1.4.0 docker compose -f compose.demo.yaml up -d   # pin a release
```

Every image is pulled from Docker Hub, defaulting to `:latest`; the three images are released together and
should always run at the same version. The backend runs permit-all, so there is **no login and no accounts** —
every request resolves to the default-showcase owner. Consequently nothing that needs Keycloak is here: no
Keycloak, no Mailpit, no Caddy. The backend runs its own Flyway migrations on startup, so there is no separate
migration service.

This is the **one file with no pinned `name:`** — it is meant to be downloaded to an arbitrary directory, and
its project name is that directory's, which is what an existing demo user's `postgres_data` volume is already
scoped to. Pinning a name would silently orphan that data. The database publishes no host port (nothing
outside the compose network needs it, and it avoids colliding with a host Postgres); data lives in the named
`postgres_data` volume and survives `down`, restarts, and image updates.

## `compose.e2e.yaml` + `compose.e2e.secured.yaml` — the release-gate overlays

Port-remap overlays, **never used alone**. The release gate stands up throwaway stacks while a dev stack may
be running, so every host port moves out of the dev range. Both halves of the protection are required — the
overlay *and* a distinct `-p` project name:

```
# demo gate
docker compose -p pensieve-e2e -f dockerCompose/compose.demo.yaml -f dockerCompose/compose.e2e.yaml up -d

# secured gate
docker compose -p pensieve-e2e -f dockerCompose/compose.secured.yaml \
               -f dockerCompose/compose.e2e.yaml -f dockerCompose/compose.e2e.secured.yaml up -d

docker compose -p pensieve-e2e ... down -v     # teardown
```

**Without `-p`, the secured form resolves to the dev stack's pinned project name and `down -v` destroys the
dev database and Keycloak data.** These overlays pin no `name:` of their own precisely so that `-p` stays the
single explicit control. `scripts/e2e-gate.sh` wires all of this up for you.

Port map (dev → gate): frontend 4200 → 14200 · backend 8080 → 18080 · mcp 8090 → 18090 · db 5432 → 15432 ·
keycloak 8081 → 18081 · mailpit 8025 → 18025. Playwright's own dev server stays on host 3000 — it is not a
container.

The split into two files is not stylistic: an overlay entry for a service the base file does not define is a
compose error, and the demo stack has no `keycloak` or `mailpit`. So `compose.e2e.yaml` remaps only the four
services **both** stacks define, and `compose.e2e.secured.yaml` remaps the two that exist only in the secured
stack (and moves `KC_HOSTNAME` to 18081 to match the issuer overrides).

One asymmetry to remember: the **issuer** moves with Keycloak's host port, but the **audience** stays
`http://localhost:8090/mcp` everywhere. It is a string equality check against the realm import's baked
audience mapper, not a reachable URL — remapping it would fail every token.

## `compose.production.yaml` — the hosted deployment

Caddy is the **only** service publishing ports (80/443); it terminates TLS and reverse-proxies three
hostnames — the web app, the MCP sidecar, and Keycloak. Everything else (backend, both databases, keycloak,
mcp) is private to the compose network with no host ports.

```
docker compose -f /opt/pensieve/dockerCompose/compose.production.yaml up -d
```

Differences from the secured dev stack, beyond TLS:

- **Two databases** — the app's `db` and Keycloak's own `keycloak-db`, each with its own volume.
- **Production realm** — Keycloak imports `keycloak/import-prod/pensieve-realm.json`, not the dev realm. It
  has no test users, no dev test client, no anonymous DCR, and `sslRequired=external`. Its
  deployment-specific values (audience URL, redirect URIs, web client secret, SMTP) are `${...}` placeholders
  Keycloak resolves from this service's environment at import time.
- **Keycloak runs `start`, not `start-dev`**, behind `KC_PROXY_HEADERS: xforwarded`.
- **`APP_ORIGIN` is required on the frontend.** Next's standalone server binds `0.0.0.0:3000` and
  `request.url` reports that bind address rather than the proxied Host, so without it login dies on
  "Invalid parameter: redirect_uri". It's configuration rather than trusted `X-Forwarded-Host` on purpose —
  a spoofable value here would be an open redirect.
- **`MCP_AUTH_MODE: required`** — the prod backend is always secured, so the sidecar enforces
  unconditionally instead of probing.
- **A basic-auth gate** in front of Keycloak's `/admin` and `/realms/master` (in the `Caddyfile`), separate
  from the Keycloak admin password.
- The project name is pinned to `pensieve`, so `down -v` means the same stack no matter which directory the
  operator is standing in.

**After the first deploy, verify** (the realm import runs once, on first boot with an empty `keycloak-db`):
decode an access token and check `aud == https://${MCP_DOMAIN}/mcp` and
`iss == https://${AUTH_DOMAIN}/realms/pensieve` — a literal `"${PENSIEVE_...}"` anywhere means placeholder
substitution failed; confirm anonymous client registration is rejected; and pre-register remote MCP hosts
(e.g. claude.ai connectors) as clients in the admin console, since there is no anonymous DCR in production.

---

## The environment files

### `.env.production.example` → copy to `.env`

Compose auto-loads `.env` from the project directory — the directory holding the compose file — so on the
Droplet the real file is `/opt/pensieve/dockerCompose/.env` and **no `--env-file` flag is needed**.

Copy the example, fill in every value, and never commit the result (`.gitignore` covers it at this depth). It
covers the three public hostnames (each needs a DNS record for Caddy's ACME challenge), the ACME email, five
secrets (`SESSION_SECRET`, `OIDC_CLIENT_SECRET`, `POSTGRES_PASSWORD`, `KC_DB_PASSWORD`, the Keycloak bootstrap
admin), the Caddy basic-auth credential, and the SMTP relay Keycloak sends verification and password-reset
mail through.

Two traps the file calls out: **a missing `.env` is not a parse-time failure** — compose prints one
`variable is not set` warning per `${VAR}`, interpolates blanks, and the stack then dies on boot (Postgres
refuses a blank password, Caddy gets an empty hostname), so read the warnings. And the **bcrypt hash must be
single-quoted**, because it contains `$` signs. `scripts/deploy-production.sh` turns the missing-file case
into a hard error by asserting the file exists in preflight.

### `.env.rehearsal` — generated, gitignored

Fills the same variables for the local production rehearsal. `make rehearse` (→ `scripts/prod-rehearsal.sh`)
generates it on first run and stands the **unmodified** production compose file and `Caddyfile` up locally
under a separate project name, verifies it, and tears it down. Override with
`make rehearse ENV_FILE=path/to/.env` to use one you filled in yourself.

---

## Which file do I want?

- **Working on the app locally, don't care about auth** → `compose.unsecured.yaml`
- **Working on auth, roles, RLS, or running the seed script** → `compose.secured.yaml`
- **Showing someone the app, or running it on a machine with no checkout** → `compose.demo.yaml`
- **Running the release gate** → `scripts/e2e-gate.sh` (which applies the `e2e` overlays)
- **Deploying, or rehearsing a deploy** → `compose.production.yaml`, via `make rehearse` locally or
  `scripts/deploy-production.sh` on the host
