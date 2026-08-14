#!/usr/bin/env bash
#
# E2E gate: stand up a throwaway stack, wait for readiness, seed it (secured mode), run the FULL
# Playwright suite against it, tear it down. One gate run = one of the two release passes:
#
#   ./scripts/e2e-gate.sh secured /path/to/the-game-pensieve-web-v2   # Gate A: secured stack, seeded,
#                                                                     #         SECURED_BACKEND=1
#   ./scripts/e2e-gate.sh demo    /path/to/the-game-pensieve-web-v2   # Gate B: pull-and-run demo stack,
#                                                                     #         unseeded (permit-all
#                                                                     #         cannot be seeded)
#
# Both passes run the complete suite: mode-conditional specs gate themselves (auth.spec.ts and
# showcases.spec.ts skip their secured blocks without SECURED_BACKEND=1; unsecured.spec.ts self-skips
# against a secured backend; auth.setup.ts probes the heartbeat and adapts).
#
# ISOLATION — the whole point. The stack runs under compose project `pensieve-e2e` with every host
# port remapped by the dockerCompose/compose.e2e*.yaml overlays, so it can never touch the dev stack
# (project `the-game-pensieve-api`, pinned in the compose files — a bare `down -v` would destroy the
# dev database). -p on the command line outranks that pinned name, which is what keeps the two apart.
# Teardown is trap-based, so a failed run still cleans up; set KEEP_STACK=1 to skip teardown and poke
# at a failed stack (`docker compose -p pensieve-e2e ... down -v` when done — the next run also
# starts with a clean `down -v`, so a kept stack never contaminates it).
#
# WHAT IT GATES: the sethcondie/*:${PENSIEVE_TAG:-latest} images currently in the LOCAL docker image
# store (the release script builds them in its step 3, immediately before calling this) — except the
# secured pass's backend, which compose builds from ./target/*.jar + the same Dockerfile, i.e. the
# same artifact by another name. Images are preflight-checked to exist locally; base images (postgres)
# may still be pulled on first use.
#
# Inputs (arguments and environment only — never prompts; see pipeline notes §3.5):
#   $1                 mode: secured | demo                                  (required)
#   $2                 path to the web repo (Playwright lives there)         (required)
#   PENSIEVE_TAG       image tag under test                                  (default: latest)
#   PLAYWRIGHT_ARGS    extra args appended to `npx playwright test`, e.g. a spec file or --workers
#   KEEP_STACK=1       skip teardown (debugging)
#   E2E_KC_USER/_PASSWORD, ADMIN_EMAIL/_PASSWORD    forwarded to Playwright / the seeder if set
#
# The host machine needs: docker, curl, jq, node/npx with the web repo's node_modules installed and
# Playwright browsers present, and port 3000 free (Playwright starts its own Next dev server there —
# CI=1 is set, so it refuses to reuse a server it did not start).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_DIR="$REPO_ROOT/dockerCompose"

PROJECT="pensieve-e2e"
BACKEND_URL="http://localhost:18080"
KEYCLOAK_URL="http://localhost:18081"
PENSIEVE_TAG="${PENSIEVE_TAG:-latest}"
export PENSIEVE_TAG

log()  { printf '\n=== [e2e-gate] %s (t+%ss)\n' "$*" "$(( $(date +%s) - START_TS ))"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
START_TS="$(date +%s)"

# --- arguments ---------------------------------------------------------------------------------
MODE="${1:-}"
WEB_REPO="${2:-}"
case "$MODE" in
    secured)
        COMPOSE_FILES=(-f "$COMPOSE_DIR/compose.secured.yaml" -f "$COMPOSE_DIR/compose.e2e.yaml" -f "$COMPOSE_DIR/compose.e2e.secured.yaml")
        GATE_PORTS=(14200 18080 18090 15432 18081 18025 3000)
        EXPECTED_SECURE_MODE=true
        ;;
    demo)
        COMPOSE_FILES=(-f "$COMPOSE_DIR/compose.demo.yaml" -f "$COMPOSE_DIR/compose.e2e.yaml")
        GATE_PORTS=(14200 18080 18090 15432 3000)
        EXPECTED_SECURE_MODE=false
        ;;
    *) fail "usage: e2e-gate.sh <secured|demo> <web-repo-path>" ;;
esac
[[ -n "$WEB_REPO" ]] || fail "usage: e2e-gate.sh <secured|demo> <web-repo-path>"
[[ -f "$WEB_REPO/playwright.config.ts" ]] || fail "no playwright.config.ts in '$WEB_REPO' — not the web repo?"

compose() { docker compose -p "$PROJECT" "${COMPOSE_FILES[@]}" "$@"; }

# --- preflight ---------------------------------------------------------------------------------
log "preflight ($MODE mode)"
command -v jq >/dev/null || fail "jq is required"
command -v curl >/dev/null || fail "curl is required"
docker info >/dev/null 2>&1 || fail "docker daemon is not running"
[[ -d "$WEB_REPO/node_modules" ]] || fail "web repo has no node_modules — run 'npm ci' in $WEB_REPO first"

# The images under test must already exist locally; pulling here would silently gate whatever is on
# Docker Hub instead of what was just built.
IMAGES=("sethcondie/the-game-pensieve-web:$PENSIEVE_TAG" "sethcondie/the-game-pensieve-mcp:$PENSIEVE_TAG")
[[ "$MODE" == demo ]] && IMAGES+=("sethcondie/the-game-pensieve-api:$PENSIEVE_TAG")
for img in "${IMAGES[@]}"; do
    docker image inspect "$img" >/dev/null 2>&1 || fail "image '$img' not found locally — build it first"
done
if [[ "$MODE" == secured ]]; then
    # compose builds the backend from the jar (dockerCompose/compose.unsecured.yaml's build block).
    ls "$REPO_ROOT"/target/*.jar >/dev/null 2>&1 || fail "no jar in target/ — run './mvnw install -DskipTests' first"
fi

# Gate ports must be free. A listener on one of them is either a leftover KEEP_STACK stack or an
# unrelated process; starting anyway would produce confusing partial failures.
for port in "${GATE_PORTS[@]}"; do
    if (echo >"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then
        fail "port $port is already in use (leftover stack? 'docker compose -p $PROJECT ${COMPOSE_FILES[*]} down -v')"
    fi
done

# --- stack up ----------------------------------------------------------------------------------
# The bind mounts (keycloak import, flyway config) and the backend's build context are relative to
# dockerCompose/, so compose resolves them the same from any working directory. The cd is kept only
# so the jar glob and any relative output below mean the repo root.
cd "$REPO_ROOT"

# Clean start: a previous KEEP_STACK run, or a crash between trap-set and up, must not leak into
# this run. --remove-orphans clears services from the other mode's file set under the same project.
compose down -v --remove-orphans >/dev/null 2>&1 || true

teardown() {
    status=$?
    if [[ $status -ne 0 ]]; then
        printf '\n=== [e2e-gate] FAILED (exit %s) — last container logs ===\n' "$status"
        compose logs --tail=40 || true
    fi
    if [[ "${KEEP_STACK:-0}" == "1" ]]; then
        printf '\n=== [e2e-gate] KEEP_STACK=1 — stack left running under project %s\n' "$PROJECT"
    else
        compose down -v --remove-orphans || true
    fi
    exit "$status"
}
trap teardown EXIT

log "starting $MODE stack (project: $PROJECT, tag: $PENSIEVE_TAG)"
compose up -d --build --remove-orphans

# --- readiness ---------------------------------------------------------------------------------
# depends_on waits for container start, not service readiness; every wait below is a real check.
# wait_for DESCRIPTION TIMEOUT_SECONDS CMD... — polls every 3s until CMD succeeds.
wait_for() {
    local desc="$1" timeout="$2" deadline
    shift 2
    deadline=$(( $(date +%s) + timeout ))
    until "$@" >/dev/null 2>&1; do
        if (( $(date +%s) >= deadline )); then
            fail "timed out after ${timeout}s waiting for: $desc"
        fi
        sleep 3
    done
    log "ready: $desc"
}

check_heartbeat() {
    curl -fsS -m 2 "$BACKEND_URL/v1/heartbeat" | jq -e ".data.secureMode == $EXPECTED_SECURE_MODE"
}
check_realm() {
    curl -fsS -m 2 "$KEYCLOAK_URL/realms/pensieve" | jq -e '.realm == "pensieve"'
}

wait_for "postgres accepting connections" 90 compose exec -T db pg_isready -U postgres -d pensieve-db
# First boot runs every Flyway migration; the JVM itself is the slow part after that.
wait_for "backend heartbeat (secureMode=$EXPECTED_SECURE_MODE)" 180 check_heartbeat
# The Playwright suite drives its OWN dev server, so these two containers are exercised by nothing
# below — without an explicit wait a crash-looping frontend or mcp image sails through the gate.
# That happened: the BFF's fail-fast SESSION_SECRET guard crash-looped the frontend in every compose
# stack that lacked the variable, and the 1.0.0 release dry run only caught it at the step-7 smoke.
# /api/auth/session is the image's own baked-in healthcheck route (see the web repo's Dockerfile).
wait_for "frontend container /api/auth/session" 120 curl -fsS -m 2 "http://localhost:14200/api/auth/session"
wait_for "mcp container /healthz" 60 curl -fsS -m 2 "http://localhost:18090/healthz"
if [[ "$MODE" == secured ]]; then
    # Realm import runs on first boot with the project-scoped (fresh) keycloak volume: 30-60s.
    wait_for "keycloak realm 'pensieve'" 180 check_realm
fi

# --- seed (secured only) -----------------------------------------------------------------------
if [[ "$MODE" == secured ]]; then
    log "seeding test data"
    # SQL_CMD must exec into THIS project's db — the seeder's default targets the dev stack.
    BASE_URL="$BACKEND_URL" \
    KEYCLOAK_URL="$KEYCLOAK_URL" \
    SQL_CMD="docker compose -p $PROJECT ${COMPOSE_FILES[*]} exec -T db psql -U postgres -d pensieve-db" \
        "$SCRIPT_DIR/seed-test-data.sh"
fi

# --- playwright --------------------------------------------------------------------------------
# CI=1: forbidOnly, retries=2, workers=1, and Playwright refuses to reuse a dev server it didn't
# start. The suite drives its own `npm run dev` on port 3000; explicit env beats .env.development,
# so the dev server (and the seeder-credential specs) talk to the gate stack, not the dev one.
log "running playwright suite"
PW_ENV=(
    CI=1
    API_BASE_URL="$BACKEND_URL/v1"
)
if [[ "$MODE" == secured ]]; then
    PW_ENV+=(
        SECURED_BACKEND=1
        OIDC_ISSUER="$KEYCLOAK_URL/realms/pensieve"
    )
fi
(
    cd "$WEB_REPO"
    # shellcheck disable=SC2086  # PLAYWRIGHT_ARGS is deliberately word-split
    env "${PW_ENV[@]}" npx playwright test ${PLAYWRIGHT_ARGS:-}
)

log "gate PASSED ($MODE)"
