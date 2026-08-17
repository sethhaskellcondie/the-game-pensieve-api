#!/usr/bin/env bash
#
# DEPLOY, SERVER SIDE — runs ON THE DROPLET at /opt/pensieve, invoked by scripts/deploy-production.sh.
#
#   (never run this from a workstation; the wrapper's SSH bootstrap is the only intended caller:
#    it fetches, checks out v$VERSION, and THEN exec's this file — so the file that runs is the one
#    inside the tag being deployed, and bash never reads a script that git is rewriting underneath it)
#
# THE NINE STEPS, each earning its place:
#   1. assert    .env + Caddyfile present, tools present — compose only WARNS on a missing .env and
#                boots a stack with blank secrets that dies later; this assert is what actually stops it
#   2. record    what is running right now (images + git ref) — rollback is one command away only if
#                you know what to roll back TO, so it is printed before anything changes
#   3. verify    HEAD is exactly v$VERSION and the compose file carries three :$VERSION pins — the
#                deploy moves the compose file, Caddyfile, and realm files together, not just images
#   4. backup    pg_dump BOTH databases to $BACKUP_DIR — losing keycloak-db loses every user account
#                (users.keycloak_sub references it); every deploy is preceded by a backup, no exceptions
#   5. pull      the slow part, while the old version is still serving
#   6. up -d     the switch; only changed services restart; expect 10-60s of downtime; then a
#                graceful `caddy reload`, because compose never recreates for a bind-mounted config
#                change alone — without it a Caddyfile-only change (or rollback) silently never lands
#   7. health    wait for the public URLs to answer correctly, then assert the running containers are
#                actually :$VERSION — the step most often skipped by hand, and the only one that
#                answers "did it work?"; a deploy that silently half-worked is the failure mode this
#                script exists to prevent. DO NOT WEAKEN IT.
#   8. prune     old images fill a 4 GB disk faster than you would think
#   9. log       append what/when/from-where to $BACKUP_DIR/deploy.log — the audit trail
#
# ON FAILURE the exit trap names the step that died and prints the rollback command. ROLLBACK IS THIS
# SAME SCRIPT WITH THE PREVIOUS TAG (run from the workstation: ./scripts/deploy-production.sh <prev>).
# There is deliberately no rollback.sh. Migrations are forward-only (kept additive by convention), so
# rolling back the image NEVER rolls back the schema — see §4.5 of the pipeline doc.
#
# WHAT A GREEN RUN DOES NOT PROVE: that a login completes (nothing here drives a browser), that email
# reaches an inbox, or anything about the realm's one-shot import (it ran on the FIRST boot only; the
# first-deploy checklist in compose.production.yaml's header covers that, once, by hand).
#
# DRY_RUN=yes rehearses read-only: steps 1-3 really run (3 verifies the TAG's content via `git show`
# instead of HEAD, because the wrapper deliberately does not move the checkout in a dry run), steps
# 4-9 print exactly what they would do. Nothing is backed up, pulled, restarted, pruned, or logged.
#
# Inputs (arguments and environment only — never prompts; conventions §3.5):
#   $1            version being deployed (required; the wrapper already validated shape + existence)
#   DRY_RUN       yes|no (default no; set by the wrapper)
#   BACKUP_DIR    where dumps and the deploy log live (default /opt/pensieve-backups — OUTSIDE the
#                 repo checkout and outside every compose volume, so a re-clone or a compose mistake
#                 cannot take the backups with it)
#
# The Droplet needs: docker + compose plugin, git, curl, jq, gzip.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

COMPOSE_FILE="$REPO_ROOT/dockerCompose/compose.production.yaml"
ENV_FILE="$REPO_ROOT/dockerCompose/.env"
BACKUP_DIR="${BACKUP_DIR:-/opt/pensieve-backups}"
DEPLOY_LOG="$BACKUP_DIR/deploy.log"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

VERSION="${1:-}"
[[ -n "$VERSION" ]] || fail "usage: deploy-production-remote.sh <version>   (env: DRY_RUN=yes|no, BACKUP_DIR)"
DRY_RUN="${DRY_RUN:-no}"
[[ "$DRY_RUN" == "yes" || "$DRY_RUN" == "no" ]] || fail "DRY_RUN must be 'yes' or 'no' (got '$DRY_RUN')"

# --env-file is passed explicitly even though compose would auto-load it from the file's directory:
# explicit turns "file missing" into a hard error instead of one warning per variable + blank values.
compose() { docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"; }

# --- step bookkeeping (the release.sh pattern) --------------------------------------------------
CURRENT_STEP="(not started)"
PREVIOUS_VERSION=""
step() { CURRENT_STEP="$1"; printf '\n=== [deploy-remote] step: %s\n' "$1"; }
on_exit() {
    local status=$?
    if [[ $status -ne 0 ]]; then
        printf '\n=== [deploy-remote] FAILED (exit %s) during step: %s\n' "$status" "$CURRENT_STEP" >&2
        if [[ -n "$PREVIOUS_VERSION" && "$PREVIOUS_VERSION" != "(none)" ]]; then
            printf 'rollback (from the workstation): ./scripts/deploy-production.sh %s\n' "$PREVIOUS_VERSION" >&2
        else
            printf 'no previous version was running — nothing to roll back to (first deploy).\n' >&2
        fi
    fi
    exit "$status"
}
trap on_exit EXIT

# wait_for DESCRIPTION TIMEOUT_SECONDS SERVICE CMD... — poll every 3s; on timeout, dump the
# service's logs first so a failed deploy explains itself without a second round trip.
wait_for() {
    local desc="$1" timeout="$2" service="$3" deadline
    shift 3
    deadline=$(( $(date +%s) + timeout ))
    until "$@" >/dev/null 2>&1; do
        if (( $(date +%s) >= deadline )); then
            printf '\n--- %s logs (last 40 lines) ---\n' "$service"
            compose logs --tail 40 "$service" 2>&1 || true
            fail "timed out after ${timeout}s waiting for: $desc"
        fi
        sleep 3
    done
    printf 'ready: %s\n' "$desc"
}

# ================================================================================================
step "1. assert environment"
# ================================================================================================
[[ "$REPO_ROOT" == "/opt/pensieve" ]] \
    || printf 'WARNING: running from %s, not /opt/pensieve — fine for a rehearsal, wrong on the Droplet\n' "$REPO_ROOT"
[[ -f "$COMPOSE_FILE" ]] || fail "$COMPOSE_FILE not found"
[[ -f "$ENV_FILE" ]] || fail "$ENV_FILE not found — compose only WARNS without it and boots blank secrets; create it from .env.production.example (values in the password manager)"
[[ -f "$REPO_ROOT/Caddyfile" ]] || fail "$REPO_ROOT/Caddyfile not found — compose bind-mounts it"
for tool in docker git curl jq gzip; do
    command -v "$tool" >/dev/null || fail "$tool is required (apt-get install -y $tool)"
done
docker compose version >/dev/null 2>&1 || fail "the docker compose plugin is required"
# The three public hostnames drive step 7's checks; read them from .env rather than sourcing it
# (sourcing executes the file — a parse step must not be able to run anything).
env_val() { sed -n "s/^$1=//p" "$ENV_FILE" | tail -1; }
APP_DOMAIN="$(env_val APP_DOMAIN)"
MCP_DOMAIN="$(env_val MCP_DOMAIN)"
AUTH_DOMAIN="$(env_val AUTH_DOMAIN)"
[[ -n "$APP_DOMAIN" && -n "$MCP_DOMAIN" && -n "$AUTH_DOMAIN" ]] \
    || fail ".env is missing APP_DOMAIN / MCP_DOMAIN / AUTH_DOMAIN"

# ================================================================================================
step "2. record what is running"
# ================================================================================================
# Printed BEFORE anything changes: this block is the rollback reference. The backend's image tag is
# the previous version — all three services always deploy together, pinned to one version.
backend_container="$(compose ps -q backend 2>/dev/null || true)"
if [[ -n "$backend_container" ]]; then
    running_image="$(docker inspect --format '{{.Config.Image}}' "$backend_container")"
    PREVIOUS_VERSION="${running_image##*:}"
    printf 'currently running (rollback target): %s\n' "$PREVIOUS_VERSION"
    compose ps --format 'table {{.Service}}\t{{.Image}}\t{{.Status}}' || true
else
    PREVIOUS_VERSION="(none)"
    printf 'nothing is currently running — this is the first deploy.\n'
fi
printf 'current git ref: %s\n' "$(git -C "$REPO_ROOT" log -1 --format='%h %d %s' 2>/dev/null || echo 'unknown')"

# ================================================================================================
step "3. verify the checkout matches v$VERSION"
# ================================================================================================
if [[ "$DRY_RUN" == "yes" ]]; then
    # The wrapper did not move the checkout, so verify the TAG's content instead of the working tree.
    git -C "$REPO_ROOT" rev-parse -q --verify "refs/tags/v$VERSION" >/dev/null \
        || fail "tag v$VERSION not present after fetch"
    [[ "$(git -C "$REPO_ROOT" show "v$VERSION:dockerCompose/compose.production.yaml" | grep -c ":$VERSION")" == "3" ]] \
        || fail "tag v$VERSION's compose file does not carry three :$VERSION pins"
    printf 'dry run: tag v%s exists and its compose file pins :%s three times.\n' "$VERSION" "$VERSION"
else
    [[ "$(git -C "$REPO_ROOT" rev-parse HEAD)" == "$(git -C "$REPO_ROOT" rev-parse "v$VERSION^{commit}")" ]] \
        || fail "HEAD is not at v$VERSION — the bootstrap checkout did not happen; do not run this script directly"
    [[ "$(grep -c "image: sethcondie/the-game-pensieve-.*:$VERSION" "$COMPOSE_FILE")" == "3" ]] \
        || fail "compose file does not carry three :$VERSION pins — was this tag made by release.sh?"
    printf 'HEAD is v%s and the compose file pins :%s three times.\n' "$VERSION" "$VERSION"
fi

# ================================================================================================
step "4. back up both databases"
# ================================================================================================
STAMP="$(date +%Y%m%d-%H%M%S)"
if [[ "$DRY_RUN" == "yes" ]]; then
    printf 'dry run: would pg_dump db (pensieve-db) and keycloak-db (keycloak) to %s/{pensieve-db,keycloak-db}-%s.sql.gz\n' "$BACKUP_DIR" "$STAMP"
elif [[ "$PREVIOUS_VERSION" == "(none)" ]]; then
    printf 'first deploy — no databases to back up yet.\n'
else
    mkdir -p "$BACKUP_DIR"
    compose exec -T db pg_dump -U postgres pensieve-db | gzip > "$BACKUP_DIR/pensieve-db-$STAMP.sql.gz"
    compose exec -T keycloak-db pg_dump -U keycloak keycloak | gzip > "$BACKUP_DIR/keycloak-db-$STAMP.sql.gz"
    # An empty dump is a failed dump that exited 0 somewhere; refuse to continue on top of one.
    for f in "$BACKUP_DIR/pensieve-db-$STAMP.sql.gz" "$BACKUP_DIR/keycloak-db-$STAMP.sql.gz"; do
        [[ -s "$f" ]] || fail "backup $f is empty — refusing to deploy without a real backup"
    done
    ls -lh "$BACKUP_DIR/pensieve-db-$STAMP.sql.gz" "$BACKUP_DIR/keycloak-db-$STAMP.sql.gz"
fi

# ================================================================================================
step "5. pull :$VERSION images"
# ================================================================================================
if [[ "$DRY_RUN" == "yes" ]]; then
    printf 'dry run: would run `docker compose pull` (the old version keeps serving meanwhile).\n'
else
    compose pull
fi

# ================================================================================================
step "6. up -d (the switch)"
# ================================================================================================
if [[ "$DRY_RUN" == "yes" ]]; then
    printf 'dry run: would run `docker compose up -d --remove-orphans` — expect 10-60s of downtime —\n'
    printf 'dry run: then gracefully reload caddy so the checked-out Caddyfile is the one serving.\n'
else
    compose up -d --remove-orphans
    # A bind-mounted config is invisible to `up -d`: compose only recreates on image/definition
    # changes, so a deploy (or rollback) whose Caddyfile differs can leave caddy serving the OLD
    # config from memory — found by the deliberate 1.0.0 rollback test (2026-08-17), where the
    # rolled-back Caddyfile on disk never took effect. `caddy reload` is a zero-downtime graceful
    # config swap and a no-op when the container was just recreated anyway. Retried briefly because
    # a just-recreated caddy may still be booting; a reload that never succeeds fails the deploy —
    # otherwise step 7 would green-light health checks served by the previous version's config.
    reload_ok=no
    for _ in 1 2 3 4 5; do
        if compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile >/dev/null 2>&1; then
            reload_ok=yes
            break
        fi
        sleep 3
    done
    [[ "$reload_ok" == "yes" ]] || fail "caddy did not accept a config reload — the tag's Caddyfile is not serving (invalid config, or caddy is down)"
    printf 'caddy reloaded — the checked-out Caddyfile is the config now serving.\n'
fi

# ================================================================================================
step "7. health + version verification"
# ================================================================================================
if [[ "$DRY_RUN" == "yes" ]]; then
    printf 'dry run: would wait for, in order:\n'
    printf '  app chain   https://%s/api/heartbeat    -> .status=="online" and .secureMode==true (300s)\n' "$APP_DOMAIN"
    printf '  keycloak    https://%s/realms/pensieve  -> .realm=="pensieve" (300s)\n' "$AUTH_DOMAIN"
    printf '  mcp         https://%s/healthz          -> HTTP 200 (120s)\n' "$MCP_DOMAIN"
    printf 'then assert the running backend/frontend/mcp containers are all :%s.\n' "$VERSION"
else
    # The app-chain check is the most valuable single probe in the system: it crosses Caddy, TLS, the
    # frontend, the private network, and the backend, AND asserts secured mode — a dropped `secured`
    # profile fails here as a named check, not as a silent fail-open (audit B3).
    wait_for "app chain https://$APP_DOMAIN/api/heartbeat (online + secured)" 300 frontend \
        bash -c "curl -fsS -m 5 'https://$APP_DOMAIN/api/heartbeat' | jq -e '.status == \"online\" and .secureMode == true'"
    wait_for "keycloak https://$AUTH_DOMAIN/realms/pensieve" 300 keycloak \
        bash -c "curl -fsS -m 5 'https://$AUTH_DOMAIN/realms/pensieve' | jq -e '.realm == \"pensieve\"'"
    wait_for "mcp https://$MCP_DOMAIN/healthz" 120 mcp \
        curl -fsS -m 5 "https://$MCP_DOMAIN/healthz"
    for service in backend frontend mcp; do
        cid="$(compose ps -q "$service")"
        [[ -n "$cid" ]] || fail "$service has no running container after up -d"
        image="$(docker inspect --format '{{.Config.Image}}' "$cid")"
        [[ "$image" == *":$VERSION" ]] \
            || fail "$service is running $image, not :$VERSION — the switch did not take"
    done
    printf 'all three services are running :%s and the public URLs answer correctly.\n' "$VERSION"
fi

# ================================================================================================
step "8. prune old images"
# ================================================================================================
if [[ "$DRY_RUN" == "yes" ]]; then
    printf 'dry run: would run `docker image prune -f`.\n'
else
    docker image prune -f
fi

# ================================================================================================
step "9. deploy log"
# ================================================================================================
if [[ "$DRY_RUN" == "yes" ]]; then
    printf 'dry run: would append to %s:  %s  deployed=%s  previous=%s\n' \
        "$DEPLOY_LOG" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$VERSION" "$PREVIOUS_VERSION"
    printf '\n=== [deploy-remote] %s dry run complete — NOTHING was changed on this host ===\n' "$VERSION"
else
    mkdir -p "$BACKUP_DIR"
    printf '%s  deployed=%s  previous=%s  by=%s\n' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$VERSION" "$PREVIOUS_VERSION" "${SUDO_USER:-$(id -un)}" >> "$DEPLOY_LOG"
    printf '\n=== [deploy-remote] %s deployed and verified (previous: %s) ===\n' "$VERSION" "$PREVIOUS_VERSION"
    printf 'rollback if needed (from the workstation): ./scripts/deploy-production.sh %s\n' "$PREVIOUS_VERSION"
fi
