# Script Explainer

Four scripts live in `scripts/`. Two are gates you run yourself; one orchestrates the whole release; one
seeds a live environment with test data. They compose like this:

```
make release VERSION=x.y.z  →  scripts/release.sh  ─┬─→ scripts/e2e-gate.sh secured ─→ scripts/seed-test-data.sh
                                                    └─→ scripts/e2e-gate.sh demo

make rehearse               →  scripts/prod-rehearsal.sh        (independent — rehearses the deploy, not the release)
```

Conventions all four share: `set -euo pipefail`; **arguments and environment only, never interactive
prompts**; the repo root is resolved from the script's own location, so they work from any working directory;
non-zero exit on any failure; and every one of them is re-runnable.

| Script | What it does | Typical entry point |
|---|---|---|
| `release.sh` | Test → build → gate → publish → tag, in eight steps | `make release VERSION=1.4.0` |
| `e2e-gate.sh` | Throwaway stack + full Playwright suite, one mode per run | called by `release.sh` (steps 4 and 5) |
| `seed-test-data.sh` | Seeds a live secured stack with the multi-role test data set | `./scripts/seed-test-data.sh` |
| `prod-rehearsal.sh` | Stands the real production stack up locally with TLS and verifies it | `make rehearse` |

---

## `release.sh` — the release orchestrator

```
./scripts/release.sh <version> <web-repo-path> <mcp-repo-path>
make release VERSION=1.4.0                      # the Makefile fills in sibling repo paths
```

The whole of Pipeline A in one command, across all three repos (api, web, mcp):

1. **Preflight** — version shape (`X.Y.Z[-suffix]`), clean working trees in all three repos, tag not already
   taken, buildx builder present, `docker login` live, smoke ports free, required tools installed.
2. **Unit gates** — `./mvnw test` (api, Testcontainers, checkstyle runs bound to validate) · `npm test` (web,
   Jest) · `npm test` (mcp, Vitest).
3. **Build local images** — single-arch, host platform, tagged `:$VERSION`. These are what the gates run.
4. **Gate A: secured e2e** — `e2e-gate.sh secured`.
5. **Gate B: demo e2e** — `e2e-gate.sh demo`.
6. **Publish multi-arch + verify** — `docker buildx --platform linux/amd64,linux/arm64 --push` for all three,
   pushing `:$VERSION` and `:latest` together, then **verifying** each pushed manifest really carries both
   platforms and that `:latest` moved to the same digest. The verification is the point: a single-arch push
   is exactly what breaks the Mac-and-Windows promise, and it is invisible unless checked.
7. **Arch smoke** — steps 2–5 only ever ran host-arch binaries, so the *other* platform ships suite-untested.
   This step runs the three non-host-platform images under emulation as a tiny demo-shaped stack and hits
   each health endpoint. It catches a broken build, not a behavioral difference.
8. **Pin + tag** — bumps the three image pins in `dockerCompose/compose.production.yaml`, commits, tags
   `v$VERSION`, verifies the *tag* carries the bumped pins, and pushes the tag. The tag is what the Droplet
   deploys. **The branch itself is not pushed** — push it yourself.

**There is no fast path and no skip flag, deliberately.** Every release pays the full gate.

**`PUBLISH=no`** is a dry-run rehearsal, not a fast path — every gate still runs; only the exits close. Step 6
builds both platforms into the buildx cache instead of pushing (so there is no manifest to verify), step 7
smokes locally built emulated images under a `:$VERSION-smoke` tag, step 8 is skipped entirely, and the
clean-tree and docker-login requirements soften to warnings. Nothing leaves the machine and git is untouched.

```
PUBLISH=no ./scripts/release.sh 0.9.0-rc1 ../the-game-pensieve-web-v2 ../the-game-pensieve-mcp
```

**Re-run safety:** a *finished* release refuses to run again — the tag already exists, so it exits changing
nothing. A *failed* release can simply be rerun. On any exit the smoke containers, network, and throwaway
tags are cleaned up by a trap, and a failure names the step it died in. A step-timing summary prints at the
end.

One accepted gap worth knowing: the multi-arch publish in step 6 **rebuilds**, so the pushed artifact is not
byte-identical to the one the gates ran.

## `e2e-gate.sh` — the isolated end-to-end gate

```
./scripts/e2e-gate.sh secured /path/to/the-game-pensieve-web-v2   # Gate A: secured stack, seeded
./scripts/e2e-gate.sh demo    /path/to/the-game-pensieve-web-v2   # Gate B: pull-and-run stack, unseeded
```

One run = stand up a throwaway stack → wait for real readiness → seed it (secured only) → run the **full**
Playwright suite against it → tear it down. Both passes run the complete suite; mode-conditional specs gate
themselves (`auth.spec.ts` and `showcases.spec.ts` skip their secured blocks without `SECURED_BACKEND=1`,
`unsecured.spec.ts` self-skips against a secured backend, `auth.setup.ts` probes the heartbeat and adapts).

**Isolation is the whole point.** The stack runs under compose project `pensieve-e2e` with every host port
remapped by the `compose.e2e*.yaml` overlays, so it can never touch the dev stack — whose compose files pin
`name: the-game-pensieve-api`, where a bare `down -v` would destroy the dev database. `-p` on the command
line outranks that pinned name, and that is what keeps the two apart.

**What it gates:** the `sethcondie/*:${PENSIEVE_TAG:-latest}` images currently in the **local** Docker image
store — `release.sh` builds them in step 3 immediately before calling this. Images are preflight-checked to
exist locally; pulling here would silently gate whatever happens to be on Docker Hub instead of what was just
built. The one exception is the secured pass's backend, which compose builds from `target/*.jar` and the same
Dockerfile — the same artifact by another name.

Readiness is checked, never assumed: `depends_on` waits for container start, not service readiness, so the
script polls Postgres with `pg_isready`, the backend heartbeat for the **expected `secureMode` value**, the
frontend container's `/api/auth/session` (the image's own baked-in healthcheck route), the mcp container's
`/healthz`, and (secured only) the Keycloak realm endpoint. The frontend and mcp waits exist because the
Playwright suite drives its **own** dev server — nothing else in the gate exercises those two containers, so
without an explicit wait a crash-looping image sails through. That happened: the BFF's fail-fast
`SESSION_SECRET` guard crash-looped the frontend in every compose stack that lacked the variable, and only
the release's step-7 smoke caught it (see `PastIssues.md`).

Inputs and knobs:

| | |
|---|---|
| `$1` | mode: `secured` \| `demo` (required) |
| `$2` | path to the web repo — Playwright lives there (required) |
| `PENSIEVE_TAG` | image tag under test (default `latest`) |
| `PLAYWRIGHT_ARGS` | extra args appended to `npx playwright test` |
| `KEEP_STACK=1` | skip teardown to poke at a failed stack |
| `E2E_KC_USER` / `_PASSWORD`, `ADMIN_EMAIL` / `_PASSWORD` | forwarded to Playwright and the seeder |

Teardown is trap-based, so a failed run still cleans up and dumps the last 40 lines of container logs. Each
run also *starts* with a `down -v`, so a kept stack never contaminates the next one. The host needs docker,
curl, jq, node/npx with the web repo's `node_modules` and Playwright browsers installed, and **port 3000
free** — Playwright starts its own Next dev server there, and `CI=1` means it refuses to reuse a server it
did not start.

## `seed-test-data.sh` — the multi-role test data seeder

```
docker compose -f dockerCompose/compose.secured.yaml up -d
./scripts/seed-test-data.sh
```

Seeds the data set documented in `DevDocumentation.md` ("Seeding Multi-Role Test Data") into a **live**
environment: one bootstrap admin, eight users covering TRIAL/PAID/LAPSED, two public showcases, and a
populated default showcase. It is the live-environment consumer of the seed files in
`src/main/resources/seeders`; the `SeededUsersFixture`/`SeededDataMatrixTests` pair is the integration-test
consumer. Both run the same choreography over the same files so they cannot drift.

Per user the choreography is: create the Keycloak account → log in via the direct-access grant → first
authenticated call JIT-provisions the `users` row → pin PAID so the account can import → `POST
/v1/function/import` with the seed file → pin the *final* role → optionally grant a showcase slug. The one
statement the API cannot perform is the first admin pin, which goes through `SQL_CMD`.

**It requires the secured stack.** The permit-all build resolves every request to the default-showcase owner
as GUEST, provisions no `users` row, and answers 403 from the admin API — there is nothing to seed.

Step 0 checks every precondition up front and fails with a specific message, because each of these otherwise
surfaces much later as a confusing 401/403: the API heartbeat reports `secureMode=true`; Keycloak is
reachable with the realm imported (the error text includes the exact commands to force a re-import without
touching the app database); the Keycloak admin credentials work; and `KEYCLOAK_CLIENT` exists, is enabled,
and has **direct access grants** on.

That last one matters for production: the script has no browser, so the password grant is its only way to get
a token. The dev realm ships `pensieve-test-client` for exactly this. **The production realm deliberately
does not** — it has only the confidential `pensieve-web` client with direct grants off, so a deployment
running `keycloak/import-prod/pensieve-realm.json` cannot be seeded by this script. Do not add a direct-grant
client to the prod realm to work around it: that is a permanent weakening of a production authorization
server, and these fixtures have no business in production anyway.

Configuration is all environment variables: `BASE_URL`, `ADMIN_EMAIL`/`ADMIN_PASSWORD`, `KEYCLOAK_URL`,
`KEYCLOAK_REALM`, `KEYCLOAK_CLIENT`, `KEYCLOAK_ADMIN_USER`/`_PASSWORD`, and `SQL_CMD` — the psql command
prefix for the single bootstrap statement (default: exec into the dev stack's `db`; the e2e gate overrides it
to reach its own project's database).

It ends with **Step 5 smoke assertions** that verify the matrix it just built rather than assuming it: GUEST
reads and filters the default showcase; TRIAL import is 403; PAID filtered search is 200; LAPSED gets 200
unfiltered / 402 filtered / 403 on write; the admin API answers for the admin, rejects non-admins, and
refuses a second admin; both showcases are listed, serve *different* data, 404 on an unknown slug, and are
GUEST-scoped (a write while viewing one is 403).

**Idempotency:** rerunnable. Keycloak 409 "already exists" and imports reporting rows as existing are
tolerated; everything else exits non-zero. It targets fresh dev databases — never point it at the
integration-test database, which seeds itself.

## `prod-rehearsal.sh` — the production rehearsal

```
./scripts/prod-rehearsal.sh [env-file]      # default: dockerCompose/.env.rehearsal
make rehearse
```

Runs the **real hosted stack on your workstation**, verifies it, and tears it down. What makes it a genuine
rehearsal is that it uses `dockerCompose/compose.production.yaml` and `Caddyfile` **unmodified** — same files,
same topology, same one-shot realm import, real TLS. Only the `.env` differs, and only in its three domains.
Three facts make that possible:

1. `*.localhost` resolves to 127.0.0.1 natively on macOS and Linux — no `/etc/hosts` entry.
2. Caddy will not attempt ACME for a `.localhost` name; it issues from its own internal CA. So the Caddyfile
   needs no dry-run switch, and every check validates a **real certificate chain** (`--cacert`, never `-k`)
   exactly as production will against Let's Encrypt.
3. No container ever needs to resolve the public hostnames — the BFF uses `OIDC_INTERNAL_ISSUER` server-side
   and only compares `iss` as a string; the sidecar and backend fetch JWKS over `keycloak:8080`. Only your
   browser and this script's curl touch the public names.

**What it proves.** That the production compose file actually comes up; that the one-shot realm import
resolved all ten `${PENSIEVE_*}` placeholders; that the web client secret, redirect URIs, and audience mapper
agree with the sidecar's and backend's OAuth config; that the backend is in secured mode and publishes no
host port (a dropped `secured` profile shows up as a *named failure*, not a silent fail-open); that Caddy's
basic-auth gate covers `/admin` and `/realms/master` and does **not** cover the login pages or their
`/resources/` assets; that anonymous dynamic client registration is refused; that Flyway migrated a clean
production-shaped database; that the sidecar enforces OAuth on a tokenless `POST /mcp`; and — the strongest
one — that **a real login completes**: a throwaway user is created and the authorization-code + PKCE flow is
driven all the way through, ending in a session whose role came from the backend. That last part is the
point: the callback can only report a role after `GET /v1/auth/me` succeeds, so the secured backend has
accepted the token's `aud` and `iss`. The probe user is deleted afterwards, pass or fail.

The tally is **23 checks**, plus one more when `SMTP_TEST_TO` is set. The throwaway passwords the script
generates — `Rehearse1<hex8>`, for both the login probe and `CREATE_TEST_USER` — deliberately satisfy the
production realm's password policy (`length(12)`, mixed case, a digit). The two generators must track that
policy: tighten it in `keycloak/import-prod/pensieve-realm.json` without updating them and the login-flow
check — the strongest in the script — goes red for the wrong reason.

One check deserves singling out. The login flow is deliberately **not** a hand-built authorize URL — it
starts at `GET /api/auth/login` and follows the redirect, because the BFF derives `redirect_uri` from the
origin it believes it is serving, and behind a reverse proxy that belief can be wrong in a way nothing else
catches. The e2e gate reaches the frontend directly, so its origin is always right; Caddy exists only in this
topology.

**What it cannot prove** — do not mistake a green run for these: ACME against real DNS, the `linux/amd64`
images (this runs host arch), Droplet sizing/swap/firewall, real inbox deliverability (SPF/DKIM — the SMTP
check only proves Keycloak handed the message to the relay), and **how any of it looks** — nothing renders a
page.

**One-shot import, one-shot volumes.** Keycloak imports the realm only on first boot with an empty
`keycloak-db`, so the script **always** starts with `down -v`; a stale volume would skip the import and
quietly invalidate the entire rehearsal. It runs under `-p pensieve-prod-rehearsal`, which overrides the
compose file's pinned `name: pensieve` and is distinct from the dev stack and the e2e gate. That override is
load-bearing: without `-p`, this script's `down -v` would target the real production project.

**Fail-fast vs collect.** Readiness waits fail fast — nothing downstream can pass if the stack is not up. The
checks then all run and report together as PASS/FAIL/SKIP with a final tally, deliberately: a rehearsal costs
several minutes of boot, so you want every problem in one pass. Exit is non-zero if any check failed.

Inputs:

| | |
|---|---|
| `$1` | env file (default `dockerCompose/.env.rehearsal`) |
| `KEEP_STACK=1` | skip teardown; prints the browser follow-ups and the teardown command |
| `CREATE_TEST_USER=1` | create a login-able user in the realm (implies keeping the stack) |
| `ALLOW_PUBLIC_DOMAINS=1` | permit non-`.localhost` domains — needed only for a staging host, where Caddy **will** hit real ACME |
| `SMTP_TEST_TO=addr` | run the email check: send Keycloak's test message to this address |
| `KC_ADMIN_UI_PASSWORD` | plaintext of the bcrypt hash; enables the positive basic-auth check |

**The env file generates itself on first run** — throwaway secrets (`openssl rand`), `.localhost` domains, and
a bcrypt hash produced by `caddy hash-password` in the same image the stack runs, so the cost factor matches
production. It is written `chmod 600` and covered by `.gitignore`. The SMTP block is left as placeholders:
that is a full *topology* rehearsal, but the email half additionally needs real relay credentials edited in
plus `SMTP_TEST_TO` on the command line.

The host needs docker, curl, jq, openssl, and **ports 80 and 443 free** — production's edge cannot be
port-remapped without changing the artifact under test.

To do the visual half by hand:

```
KEEP_STACK=1 CREATE_TEST_USER=1 ./scripts/prod-rehearsal.sh
sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain <printed CA path>
# then open https://pensieve.localhost and log in as the printed user
```

---

## Which script do I want?

- **Cutting a release** → `make release VERSION=x.y.z`; rehearse it first with `PUBLISH=no`.
- **Just want the e2e suite against a clean stack** → `./scripts/e2e-gate.sh secured <web-repo>`.
- **Need real accounts and data in the local secured stack** → `./scripts/seed-test-data.sh`.
- **About to deploy, or changed anything in the production topology** → `make rehearse`.
