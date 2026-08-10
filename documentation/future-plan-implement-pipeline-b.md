# Pipeline B — Future Implementation Plan (Hosted Deployment)

**What this is.** The plan for building Pipeline B: the secured, publicly-hosted stack on a
DigitalOcean Droplet, plus the `make deploy VERSION=X.Y.Z` command that updates it. Pipeline A
(build, gate, publish to Docker Hub) is **already built**; this document carries forward what was
learned building it.

**Written to be picked up cold.** It assumes no memory of the Pipeline A work and no other document
open. Everything needed is either here or named by exact path. The original design document,
`localFiles/pipeline_notes.md`, remains the source of record for *why* the design is what it is; this
document is the *how*, corrected against what actually got built.

**Status: not started, and deliberately so.** Pipeline B is blocked on three prerequisites (§3), two
of which are external and have real lead time. Nothing below should begin until those clear.

---

## 1. Where Things Stand

### 1.1 What Pipeline A built (all of this exists and works)

| Artifact | Path | What it does |
| --- | --- | --- |
| Release orchestrator | `scripts/release.sh` | The whole of Pipeline A in 8 steps: preflight → unit gates → local build → secured e2e gate → demo e2e gate → multi-arch publish + manifest verification → cross-arch QEMU smoke → pin bump + git tag |
| E2E gate | `scripts/e2e-gate.sh` | Stands up a throwaway isolated stack (`-p pensieve-e2e` + port overlays), waits for real readiness, seeds in secured mode, runs the full Playwright suite, tears down via `trap` |
| Command aliases | `Makefile` | `make release VERSION=…` and `make deploy VERSION=…` — thin, no logic |
| Demo stack | `compose.demo.yaml` | Pull-and-run, no bind mounts, no Keycloak |
| Gate overlays | `compose.e2e.yaml`, `compose.e2e.secured.yaml` | Port remaps so a gate run cannot collide with the dev stack |
| Docs | `README.md`, `documentation/runningOptions.md` (Options 9, 14, 15), `documentation/DevDocumentation.md` | Release process and the demo are documented |

**`make deploy` already exists and already defines Pipeline B's interface.** The target is written and
waiting:

```make
deploy:
	@test -x scripts/deploy-production.sh || { \
	    echo "error: scripts/deploy-production.sh does not exist yet (Pipeline B …)"; exit 1; }
	./scripts/deploy-production.sh "$(VERSION)"
```

So the contract is fixed: **`scripts/deploy-production.sh` taking the version as its single
positional argument.** Build to that signature and the Makefile needs no change.

### 1.2 What is NOT done

- **Phase A4 — the first real publish — has not been run.** `PUBLISH=no` rehearsed the entire release
  end to end successfully, but nothing has been pushed to Docker Hub and **no version tag exists**.
  This matters for sequencing; see §3.3.
- **No part of Pipeline B exists.** No Droplet, no domain, no SMTP relay, no deploy script.
- **`compose.production.yaml` has never been run**, anywhere. It is carefully reviewed and
  cross-checked (§4.1) but wholly unexercised.

---

## 2. What Pipeline B Is

A single Ubuntu Droplet running `compose.production.yaml` unmodified, with this repo checked out at
`/opt/pensieve` and a hand-written `.env` beside it.

**The full secured topology:** Caddy (the only service with public ports — 80/443, terminating TLS via
Let's Encrypt) reverse-proxying three hostnames to private services: the Next.js frontend, the MCP
sidecar, and Keycloak. Behind those: the Spring backend, the app Postgres, and Keycloak's own separate
Postgres. Nothing but Caddy is reachable from outside.

**Pipeline B builds nothing.** It pulls the exact images Pipeline A published. One artifact set serves
both pipelines — `the-game-pensieve-api:1.4.0` is a single image, and the security posture is purely
runtime configuration. A demo user and production run identical binaries. This is why Pipeline A's
gate is also Pipeline B's gate.

**Deploys are manually triggered, always.** `make deploy VERSION=1.4.0` — one command, one argument,
no prompts. A registry push must never restart the live site. Expect **10–60s of downtime** per deploy;
zero-downtime would need a load balancer and roughly triples the complexity.

**Rollback is the same script with an older version tag.** There is deliberately no `rollback.sh` —
that would be a second code path exercised only during an emergency.

### Sizing

**4 GB Droplet (~$24/mo).** The stack needs ~2.3 GB before the OS: backend JVM ~700 MB, Keycloak
~800 MB (also a JVM), two Postgres ~200 MB each, Next.js ~200 MB, sidecar ~100 MB, Caddy ~50 MB. That
is the floor, not the comfortable choice — hence the mandatory **2 GB swap file** so Keycloak's
memory-hungry startup cannot OOM-kill a neighbour, and the absolute rule that **nothing is ever built
on this box**.

---

## 3. Prerequisites — Do These First

All three are genuine blockers. The first two are external with real lead time; start them well before
you intend to build anything.

### 3.1 SMTP relay with SPF/DKIM ⚠️ most schedule-sensitive item

Keycloak sends email verification and password reset. Without a relay these silently do not work.

**This is effectively irreversible** — see hazard §4.2. Get it right the first time.

Needed: a relay account, credentials, a `From` address the relay is authorized to send as, and SPF
(and ideally DKIM) DNS records on that domain, or the links land in spam.

Port and TLS flags are a **pair** — choose one row, never mix:

| Port | `SMTP_STARTTLS` | `SMTP_SSL` |
| --- | --- | --- |
| 587 | `true` | `false` |
| 465 | `false` | `true` |

### 3.2 Domain registration and DNS

Three hostnames, three A (and ideally AAAA) records pointed at the Droplet's IP:

| Variable | Serves | Example |
| --- | --- | --- |
| `APP_DOMAIN` | the web app | `pensieve.example.com` |
| `MCP_DOMAIN` | the MCP sidecar (resource at `/mcp`) | `mcp.example.com` |
| `AUTH_DOMAIN` | Keycloak | `auth.example.com` |

**The records must resolve before Caddy first starts**, or its ACME challenge fails. Propagation is not
instant — set them up early and verify with `dig +short <domain>` from somewhere other than your own
machine.

### 3.3 Run Phase A4 — the first real release

**Do this before B3.** Two reasons:

1. **`compose.production.yaml` currently pins `:latest` on all three images** (lines 44, 61, 79). §3.6
   of the design notes forbids deploying `:latest` to production, and the deploy script will reject it.
   The pins only become real versions when `release.sh` step 8 bumps them.
2. **No git tag exists.** The Droplet deploys by checking out `v$VERSION`. Without a tag there is
   nothing to check out.

A4 also has a known precondition of its own: **web-repo spec fixes need to be committed** before the
release gate will pass cleanly. Re-run `PUBLISH=no ./scripts/release.sh …` first to confirm the gate is
green, then run it for real.

---

## 4. Ground Truth — Verified Facts and Hazards

Read this section before writing anything. Each item was verified against the code, not assumed.

### 4.1 Already verified — do not re-derive

**The production realm import and the compose environment agree exactly.** All ten `${PENSIEVE_*}`
placeholders in `keycloak/import-prod/pensieve-realm.json` are supplied by the `keycloak` service in
`compose.production.yaml`:

```
PENSIEVE_APP_DOMAIN   PENSIEVE_MCP_DOMAIN   PENSIEVE_WEB_CLIENT_SECRET
PENSIEVE_SMTP_HOST    PENSIEVE_SMTP_PORT    PENSIEVE_SMTP_FROM
PENSIEVE_SMTP_USER    PENSIEVE_SMTP_PASSWORD
PENSIEVE_SMTP_STARTTLS PENSIEVE_SMTP_SSL
```

(Other `${...}` strings in that file — `${client_id}`, `${profileScopeConsentText}`, etc. — are
Keycloak's own i18n tokens. Leave them alone.)

**Every `${VAR}` in `compose.production.yaml` has a key in `.env.production.example`.** All seventeen
match, with `${PWD}` the only shell-provided exception (see hazard §4.3). Copying the example and
filling every blank is sufficient; nothing is missing.

### 4.2 ⚠️ Keycloak's realm import runs ONCE, on first boot with an empty `keycloak-db`

`OIDC_CLIENT_SECRET` and every `SMTP_*` value are baked into the realm at import time. **Editing `.env`
afterwards does not change them.** Correcting a mistake means destroying the `keycloak-db` volume and
re-importing — which also destroys every user account that has been created.

This single fact drives the whole shape of the plan: it is why §3.1 is a hard prerequisite, and why
Phase B0 rehearses the import locally before the Droplet ever exists.

### 4.3 ⚠️ `compose.production.yaml` bind-mounts `${PWD}` paths

Two mounts: `${PWD}/Caddyfile` and `${PWD}/keycloak/import-prod`.

`${PWD}` resolves to wherever `docker compose` is invoked, **not** to the compose file's location. An
interactive shell exports `PWD`, so this works when you type the command by hand. But under `sudo`
without `--preserve-env`, or from systemd, or any context with a scrubbed environment, `PWD` is empty —
compose then interpolates a blank string, tries to mount `/Caddyfile`, and you get a confusing failure
or, worse, a directory mounted where a file was expected.

**Two defences; do both:**

1. **Recommended change:** replace `${PWD}/` with `./` in both volume entries. Compose resolves relative
   paths against the project directory (the compose file's own directory), which is exactly the
   intended meaning and cannot be blanked by a hostile environment. This is a change to a
   carefully-reviewed artifact, so make it deliberately and verify the stack still comes up in B0.
2. **Regardless:** the deploy script must `cd /opt/pensieve` explicitly before invoking compose, and
   assert `[[ -f Caddyfile ]]` as a preflight check.

### 4.4 ⚠️ Do not let `git checkout` replace a script while that script is running

Bash reads a script file incrementally as it executes. If the remote deploy script performs
`git checkout v$VERSION` **on the checkout it is itself running from**, the file underneath it can
change mid-execution and bash will resume at a byte offset that now means something else entirely.

**The fix — sequence the checkout so the script that runs is the one already settled on disk:**

```bash
ssh "$DEPLOY_HOST" "cd /opt/pensieve \
    && git fetch --tags --prune \
    && git checkout --detach 'v$VERSION' \
    && exec bash scripts/deploy-production-remote.sh '$VERSION'"
```

The `exec bash <file>` begins only after the checkout has fully completed, and it opens the *new* file
fresh. The bootstrap itself is a tiny inline command that never changes, so it is never the thing being
swapped. One SSH call, no hazard.

### 4.5 The backend migrates itself

`spring.flyway.locations=classpath:migrations` — migrations ship inside the jar and run on startup.
**Deploying is genuinely just "pull the new image and restart."** There is no migration step to
orchestrate and no separate Flyway service in production.

Corollary: **keep migrations additive** (add a column rather than renaming one), so a rollback to the
previous image can still run against the newer schema. Image rollback is clean; Flyway is forward-only.
**Rollback does not roll back the database.**

### 4.6 Health endpoints — what each one actually proves

Confirmed against the source and already exercised by `release.sh`'s smoke step:

| Check | URL | Success condition | Proves |
| --- | --- | --- | --- |
| **App chain** | `https://${APP_DOMAIN}/api/heartbeat` | `.status == "online"` and `.secureMode == true` | **The most valuable single check.** The frontend route proxies the backend's heartbeat, so this covers Caddy → TLS → frontend → private network → backend, *and* confirms the backend is in secured mode |
| Sidecar | `https://${MCP_DOMAIN}/healthz` | HTTP 200 | Liveness only — a plain probe that does **not** prove backend connectivity |
| Keycloak realm | `https://${AUTH_DOMAIN}/realms/pensieve` | `.realm == "pensieve"` | Keycloak is up, the realm imported, and TLS works on that hostname |
| Backend (internal) | `docker compose exec` → `/v1/heartbeat` | `.data.secureMode == true` | Direct backend check; needed only for debugging, since the app chain check covers it |

The backend publishes **no** host port in production — it is reachable only on the compose network.

### 4.7 Timeouts that were learned the hard way

`depends_on` waits for container *start*, not readiness. Every wait must be a real check. Values proven
in the e2e gate and the release smoke test:

| Waiting for | Timeout | Note |
| --- | --- | --- |
| Postgres accepting connections | 90s | `pg_isready` |
| Backend heartbeat | 180s | First boot runs every Flyway migration; the JVM is the slow part after |
| Keycloak realm endpoint | 180s | Realm import on first boot takes 30–60s |
| Anything under QEMU emulation | 300s | Only relevant to the release smoke, not to production |

Production runs on real hardware with a cold image pull; **be more generous, not less** — 300s for the
backend and Keycloak on a first deploy is reasonable. Poll every 3s.

### 4.8 Other standing facts

- **Registry:** Docker Hub only. Run `docker login` on the Droplet so pulls are authenticated (anonymous
  pulls are rate-limited, and hitting that limit mid-deploy is a miserable way to find out).
- **Secrets:** `.env` lives on the Droplet only. Created by hand, `chmod 600`, never in git, with a copy
  in a password manager. **It exists in exactly one place** — treat it accordingly.
- **The production realm has no direct-access-grant client, deliberately.** `scripts/seed-test-data.sh`
  therefore **cannot** run against production, and must not be made to. Production data is real data.
- **No anonymous dynamic client registration in production.** Remote MCP hosts (e.g. claude.ai
  connectors) must be pre-registered by hand via the Keycloak admin console.
- **Tagging:** `:X.Y.Z` is immutable; `:latest` moves each release. Production deploys a version tag,
  never `:latest` — the script must reject `latest` explicitly.

---

## 5. The Phases

Seven phases. B0 is new — it exists because the irreversibility in §4.2 deserves a rehearsal. B1–B5
follow the original design's numbering so this document and `localFiles/pipeline_notes.md` stay aligned.

---

### Phase B0 — Rehearse the irreversible part locally

**Why this phase exists.** Keycloak's realm import is one-shot (§4.2). Discovering a malformed SMTP
value or an unresolved placeholder *on the Droplet* means wiping `keycloak-db` and starting over.
Discovering it on your workstation costs nothing. Pipeline A's gate proved that standing up a throwaway
isolated stack is cheap and reliable — apply the same trick here.

- [ ] Write a real `.env` locally with the **actual** production SMTP credentials and a freshly
      generated `OIDC_CLIENT_SECRET`. Use placeholder domains for `APP_DOMAIN`/`MCP_DOMAIN`/`AUTH_DOMAIN`
      (Caddy is not part of this rehearsal).
- [ ] Bring up **only** `keycloak` and `keycloak-db`, under a distinct compose project name so it cannot
      touch your dev stack:
      ```bash
      docker compose -p pensieve-kc-rehearsal -f compose.production.yaml up -d keycloak keycloak-db
      ```
- [ ] Verify the import resolved: fetch the realm and confirm **no literal `${PENSIEVE_...}` string
      survives anywhere** in the client config, the redirect URIs, or the SMTP settings. A literal
      placeholder means substitution failed.
- [ ] Log into the admin console and **send a test email** (Realm settings → Email → Test connection).
      This is the entire point of the phase: confirm the relay accepts the credentials, the `From`
      address is authorized, and the message arrives rather than landing in spam.
- [ ] If you applied the `${PWD}` → `./` change from §4.3, confirm the realm import volume still mounts.
- [ ] Tear down completely: `docker compose -p pensieve-kc-rehearsal -f compose.production.yaml down -v`

*Exit criteria: a test email from the production realm config arrives in a real inbox, and no
unresolved placeholder appears anywhere in the imported realm. Record the exact working `.env` values
in your password manager — these are the ones that go on the Droplet.*

---

### Phase B1 — Provision the Droplet

- [ ] Create a **4 GB** Ubuntu Droplet (LTS).
- [ ] Add a **2 GB swap file** (§2 — non-negotiable; Keycloak's startup is the reason). Make it
      persistent in `/etc/fstab`.
- [ ] Install Docker Engine + the Compose plugin from Docker's official repository.
- [ ] **Firewall:** allow 80 and 443 from anywhere; restrict 22 to your IP. Use DO's cloud firewall,
      `ufw`, or both.
- [ ] Enable unattended security upgrades.
- [ ] `docker login` (access token, not a password) so image pulls are authenticated.
- [ ] Create a **read-only GitHub deploy key** and `git clone` this repo to `/opt/pensieve`.
- [ ] Point the three DNS records at the Droplet's IP (§3.2) and confirm they resolve externally.

*Exit criteria: you can SSH in, `docker run hello-world` works, `/opt/pensieve` is a clean checkout,
`free -h` shows the swap, and all three hostnames resolve to this box from an outside network.*

---

### Phase B2 — Configure

- [ ] `cp .env.production.example /opt/pensieve/.env` and fill in **every** value.
- [ ] Generate each secret with `openssl rand -base64 48` — `SESSION_SECRET` (must be ≥32 chars),
      `OIDC_CLIENT_SECRET`, `POSTGRES_PASSWORD`, `KC_DB_PASSWORD`, `KC_ADMIN_PASSWORD`.
- [ ] Use the **exact SMTP values proven in B0**. Do not retype them from memory; copy them.
- [ ] `chmod 600 .env`.
- [ ] Store a complete copy in your password manager. This file exists in exactly one place.
- [ ] **Re-read §4.2 before proceeding.** The next phase's first `up` is the one-way door.

*Exit criteria: `.env` is complete, `chmod 600`, backed up, and every SMTP value is one that already
sent a real email in B0.*

---

### Phase B3 — First deploy, by hand

**Do this by hand, not with a script.** You cannot script a procedure you have not performed, and the
first deploy will surface issues best debugged with a shell open. B4 is the transcription of what
worked here — so **keep notes as you go**; they are B4's specification.

- [ ] Check out a real version tag from Phase A4: `git fetch --tags && git checkout v$VERSION`. Confirm
      the three image pins in `compose.production.yaml` are that version, not `:latest`.
- [ ] `cd /opt/pensieve && docker compose -f compose.production.yaml up -d`
- [ ] Watch it come up: `docker compose -f compose.production.yaml logs -f`. Expect Keycloak's realm
      import and the backend's Flyway run to dominate the first boot.
- [ ] Confirm Caddy obtained certificates for all three hostnames (its log states this plainly). ACME
      failures here are almost always DNS not resolving yet.

**Then work through the verification checklist in `compose.production.yaml`'s own header:**

- [ ] **Decode an access token** and confirm `aud == https://${MCP_DOMAIN}/mcp` and
      `iss == https://${AUTH_DOMAIN}/realms/pensieve`. A literal `"${PENSIEVE_...}"` anywhere means
      placeholder substitution failed — fix it and **re-import from an empty `keycloak-db`** (§4.2).
      The audience is validated by *both* the sidecar and the backend; a mismatch rejects every token.
- [ ] **Confirm anonymous client registration is rejected:**
      `POST /realms/pensieve/clients-registrations/openid-connect` must fail.
- [ ] **Pre-register any remote MCP hosts** (e.g. claude.ai connectors) as clients via the admin console.
- [ ] Run the §4.6 health checks against the **public** URLs, over HTTPS.
- [ ] Create a real user account through the normal signup flow and **confirm the verification email
      arrives**. This is the first end-to-end proof that B0's SMTP work held.
- [ ] Exercise the app: log in, create a record, log out, log back in, confirm it persisted and that you
      see only your own data.

*Exit criteria: the site is publicly reachable over HTTPS on all three hostnames, a real account can be
created and verified by email, and a decoded token carries the correct issuer and audience.*

---

### Phase B4 — The deploy script

Transcribe B3 into two scripts. See §6 for the detailed specification and §7 for the patterns to copy.

- [ ] **`scripts/deploy-production.sh <version>`** — the local wrapper. Preflight, then one SSH call.
- [ ] **`scripts/deploy-production-remote.sh <version>`** — the server-side script, living in this repo
      so it is versioned in the same commit as `compose.production.yaml`, the `Caddyfile`, and the realm
      import. Those four can then never drift apart.
- [ ] `make deploy VERSION=…` already invokes the wrapper — no Makefile change needed (§1.1).
- [ ] **Test the rollback path deliberately**, while nothing is at stake: deploy version N, deploy N-1,
      confirm the site works, deploy N again. An emergency is a terrible time to discover the rollback
      is broken.

*Exit criteria: `make deploy VERSION=X.Y.Z` completes end to end, prints truthful pass/fail, and a
rollback to the previous version has been performed successfully at least once.*

---

### Phase B5 — Backups and health checks

**Do this before there is data worth losing.** The per-deploy dump in the deploy script is not a
schedule.

- [ ] **`pg_dump` cron for BOTH databases** to DO Spaces (~$5/mo). Losing `keycloak-db` means losing
      every user account — it is not optional, and it is the one people forget.
- [ ] Enable **DO snapshots** (for "the box died"). The `pg_dump` cron is for "I need Tuesday's data" —
      they solve different problems and you want both.
- [ ] **Verify a restore actually works.** An untested backup is a hope, not a backup.
- [ ] Add `healthcheck:` blocks to `compose.production.yaml` — there are currently **none**. Without
      them `docker compose ps` reports "running" for a hung process, and `restart: unless-stopped` only
      reacts to a crash, never to a hang. Use the §4.6 endpoints.
- [ ] Add log rotation for the Docker daemon (`max-size`/`max-file` in `/etc/docker/daemon.json`) — a
      4 GB Droplet's disk fills faster than you expect.

*Exit criteria: both databases dump on a schedule to off-box storage, a restore has been rehearsed, and
`docker compose ps` reports meaningful health.*

---

### Phase B6 — Documentation

- [ ] **`README.md` § Production Deployment** — currently ends with a bare
      `docker compose -f compose.production.yaml up -d`. Replace that with `make deploy VERSION=X.Y.Z`
      as the normal path, keeping the manual command only as the documented first-bringup procedure.
- [ ] **`documentation/runningOptions.md` Option 9 — Production Stack** — add the deploy command, the
      rollback procedure, and the prerequisites.
- [ ] **`documentation/DevDocumentation.md`** — add a deployment section covering the Droplet layout
      (`/opt/pensieve`, `.env`, deploy key), the deploy sequence, rollback, and where backups land.
- [ ] Record the SMTP provider, the DNS host, and the Droplet's identity somewhere durable. In a few
      months you will not remember which registrar holds the domain.

**Repo conventions** (`CLAUDE.md`): Pipeline B touches no API behavior, so `openapi.yaml` and
`api.postman_collection.json` need no updates. `./mvnw checkstyle:check` applies only if you end up
touching Java, which this work should not.

---

## 6. The Deploy Script — Specification

### 6.1 Shape

A thin **local wrapper** that fails fast, invoking a **server-side script** over a single SSH call.

**Why split:** preflight fails locally *before* production is touched, while the real logic lives on the
Droplet, versioned in the same commit as the compose file, `Caddyfile`, and realm import — so those four
can never drift apart. The server-side script also reads `.env` directly on the box, so **no secret ever
crosses the wire**.

### 6.2 Local wrapper — `scripts/deploy-production.sh <version>`

Preflight only; every check fails in seconds and costs nothing:

1. **Version shape** is `X.Y.Z` (optionally `-suffix`) — reuse the regex from `release.sh:100`.
2. **Reject `latest` explicitly.** Never deploy a moving tag to production.
3. **The git tag `v$VERSION` exists** on the remote (`git ls-remote --tags origin`). Refuse otherwise.
4. **All three images exist on Docker Hub at that version** —
   `docker buildx imagetools inspect sethcondie/the-game-pensieve-{api,web,mcp}:$VERSION`. *Deploying a
   version whose frontend image was never pushed is the single most likely mistake, and this catches
   it.*
5. **The host is reachable** over SSH.
6. Then the one SSH call, in the exact form given in §4.4.

Take the deploy host from an environment variable (e.g. `DEPLOY_HOST`), with a sensible default —
arguments and environment only, never prompts.

### 6.3 Server-side script — `scripts/deploy-production-remote.sh <version>`

The nine steps, each earning its place:

| # | Step | Why |
| --- | --- | --- |
| 1 | Assert `cd /opt/pensieve` succeeded; `.env` and `Caddyfile` are present | §4.3 — a blank `${PWD}` or a missing file should fail here, loudly, not halfway through |
| 2 | **Record what is currently running** (`docker compose images`, and the current git ref) | Rollback is one command away only if you know what to roll back *to*. Print it prominently |
| 3 | *(Checkout already happened in the bootstrap — §4.4)* Verify `HEAD` is at `v$VERSION` and that `compose.production.yaml` carries three `:$VERSION` pins | Brings the compose file, `Caddyfile`, and realm import to the matching revision — not just the images |
| 4 | **`pg_dump` BOTH databases** to a timestamped file outside the compose volumes | Every deploy is preceded by a backup. Include `keycloak-db` — losing it means losing every account |
| 5 | `docker compose -f compose.production.yaml pull` | The slow part happens while the old version is still serving |
| 6 | `docker compose -f compose.production.yaml up -d` | The switch. Only changed services restart. **10–60s of downtime** |
| 7 | **Wait for health, then smoke-test the public URLs** (§4.6, §4.7) | The step most often skipped by hand, and the only one that answers "did it actually work?" |
| 8 | `docker image prune -f` | Old images fill a 4 GB disk faster than you would think |
| 9 | Append version + timestamp to a deploy log; **on failure, print the rollback command** | The audit trail Pipeline A otherwise lacks, and an 11pm recovery path that is a copy-paste |

Step 7 is the one that makes the whole script trustworthy. Do not weaken it.

### 6.4 Conventions — non-negotiable

From the design notes §3.5, and followed throughout Pipeline A. Each is cheap now and expensive to
retrofit; following them makes adopting hosted CI later a trigger change rather than a rewrite.

1. **Every input is an argument or environment variable — never a prompt.** A CI runner cannot answer a
   question. A confirmation gate is fine only if skippable (`CONFIRM=yes`).
2. **`set -euo pipefail` at the top of every script.** Without it, a failed step is a printed error
   followed by a cheerful continuation.
3. **No absolute home-directory paths** — resolve the repo root from the script's own location.
4. **Credentials come from the environment.** Never contain, prompt for, or store a token.
5. **Exit non-zero on failure, and make the last step a verification.**
6. **Re-runnable.** Running it twice must be safe. (Deploying the same version twice is a no-op restart —
   that is fine and correct.)
7. **Version is required and must exist as a git tag.** Refuse otherwise.
8. **Never deploy `:latest`.** Reject it explicitly.
9. **Rollback is the same script with an older version tag.** No separate rollback script.

Add a **`DRY_RUN=yes`** mode, mirroring `release.sh`'s `PUBLISH=no`: run every preflight and every
check, print exactly what would happen, change nothing. That rehearsal is what caught problems in
Pipeline A before they mattered, and it is the single highest-value thing to build early.

---

## 7. Patterns to Copy from Pipeline A

`scripts/release.sh` and `scripts/e2e-gate.sh` are working, debugged references. **Read them before
writing anything** — the deploy script should look like a sibling, not a stranger.

**Path resolution** (convention 3):
```bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
```

**Fail helper** — every check gets a specific message, so a failure names its own cause:
```bash
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
```

**Step bookkeeping with timings** (`release.sh:66-77`) — `step "4. pg_dump"` prints a banner, records
elapsed time, and leaves `CURRENT_STEP` set so the exit trap can report *which* step failed. On a
deploy, knowing that it died during "6. up -d" rather than "7. health" is the difference between calm
and panic.

**Trap-based cleanup that reports the failing step** (`release.sh:87-95`):
```bash
on_exit() {
    local status=$?
    if [[ $status -ne 0 ]]; then
        printf '\n=== FAILED (exit %s) during step: %s\n' "$status" "$CURRENT_STEP" >&2
        printf 'rollback: make deploy VERSION=%s\n' "$PREVIOUS_VERSION" >&2
    fi
    exit "$status"
}
trap on_exit EXIT
```

**The readiness poller** (`e2e-gate.sh:133-144`) — the single most reusable piece, and the main defence
against flakiness:
```bash
wait_for() {   # DESCRIPTION TIMEOUT_SECONDS CMD...
    local desc="$1" timeout="$2" deadline
    shift 2
    deadline=$(( $(date +%s) + timeout ))
    until "$@" >/dev/null 2>&1; do
        (( $(date +%s) >= deadline )) && fail "timed out after ${timeout}s waiting for: $desc"
        sleep 3
    done
    printf 'ready: %s\n' "$desc"
}
```

**URL polling with a JSON assertion** (`release.sh:252-264`) — and note the detail that makes it
useful in practice: **on timeout it dumps the container logs before failing**, so a failed deploy tells
you why without a second round trip.

```bash
wait_for "app chain healthy" 300 \
    bash -c 'curl -fsS -m 5 "https://$APP_DOMAIN/api/heartbeat" | jq -e ".status == \"online\" and .secureMode == true"'
```

**Verification as the last step, never optional** (`release.sh:193-213`). Pipeline A asserts that a
pushed manifest really carries both platforms, because a single-arch push is invisible until a user
hits it. The deploy analogue: assert the running images are the version you asked for, and that the
public URLs answer correctly. *A deploy that silently half-worked is the failure mode worth engineering
against.*

---

## 8. Acceptance — Pipeline B Is Done When

- [ ] `make deploy VERSION=X.Y.Z` updates the live site end to end, unattended, with truthful pass/fail.
- [ ] A rollback to the previous version has been performed successfully at least once.
- [ ] A wrong or unpublished version is rejected by preflight in seconds, before production is touched.
- [ ] `DRY_RUN=yes` rehearses a full deploy without changing anything.
- [ ] Both databases are dumped before every deploy **and** on an independent schedule to off-box
      storage, and a restore has been rehearsed.
- [ ] A new user can sign up, receive a verification email, log in, and see only their own data.
- [ ] MCP clients can authenticate against the production realm with a correct `aud` and `iss`.
- [ ] The deploy log records what was deployed and when.

---

## 9. Open Decisions

| Item | Notes |
| --- | --- |
| **SMTP provider** | Not yet chosen. §3.1. Irreversible after Keycloak's first boot — decide deliberately |
| **Domain and DNS host** | Not yet registered. §3.2 |
| **`${PWD}` → `./` change** | Recommended in §4.3. A change to a reviewed artifact; make it deliberately and verify in B0 |
| **DO Spaces for backups** | ~$5/mo plus a bucket. The alternative is dumping to the Droplet's own disk, which does not survive the box dying |
| **Uptime monitoring** | Not in the original design. Once the site is public, something should tell you it is down before a user does. Cheap to add — an external check against `https://${APP_DOMAIN}/api/heartbeat` |

### Known and accepted

- ~10–60s of downtime per deploy.
- Rollback does not roll back the database; keep migrations additive (§4.5).
- Nothing deploys unless a person runs the command — by design.
- arm64 images are smoke-tested but not suite-tested (a Pipeline A gap; the Droplet is amd64, so this
  does not affect production).

---

## 10. Reference

| Path | What it is |
| --- | --- |
| `localFiles/pipeline_notes.md` | The original design document — the *why* behind every decision. §3.3, §3.4, §3.5, §3.6, and §6 are the Pipeline B sections |
| `scripts/release.sh` | Pipeline A's orchestrator — the reference implementation for script conventions |
| `scripts/e2e-gate.sh` | Isolation and readiness-polling patterns |
| `compose.production.yaml` | The production topology, with a first-deploy verification checklist in its header |
| `.env.production.example` | Every production variable, annotated |
| `Caddyfile` | Edge routing and TLS for the three hostnames |
| `keycloak/import-prod/pensieve-realm.json` | The production realm — no test users, no dev client, no anonymous DCR |
| `keycloak/README.md` | Realm contents, clients, scopes, and how to mint a token by hand |
| `documentation/runningOptions.md` | Every way to run the project; Option 9 is the production stack |
| `documentation/PastIssues.md` | Previously-hit problems worth checking when something is inexplicable |
