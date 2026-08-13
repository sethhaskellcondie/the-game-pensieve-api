#!/usr/bin/env bash
#
# RELEASE ORCHESTRATOR — the whole of Pipeline A in one command:
#
#   ./scripts/release.sh <version> <web-repo-path> <mcp-repo-path>
#   e.g.  make release VERSION=1.4.0        (the Makefile fills in sibling repo paths)
#
#   1. Preflight        clean trees ×3, version shape, tag free, buildx builder, docker login
#   2. Unit gates       ./mvnw test (api, Testcontainers) · npm test (web, Jest) · npm test (mcp, Vitest)
#   3. Build local      docker build ×3, single-arch (host platform), tagged :$VERSION
#   4. Gate A: secured  scripts/e2e-gate.sh secured — seeded stack, full Playwright, SECURED_BACKEND=1
#   5. Gate B: demo     scripts/e2e-gate.sh demo    — pull-and-run stack, full Playwright
#   6. Publish          docker buildx --platform linux/amd64,linux/arm64 --push ×3 (:$VERSION + :latest)
#                       then VERIFY each manifest really carries both platforms (a single-arch push is
#                       the exact failure that breaks the Mac-and-Windows promise, and it is invisible
#                       unless checked) and that :latest moved to the same digest.
#   7. Arch smoke       run the three images for the platform the host is NOT (the suite only ever ran
#                       host-arch binaries) as a tiny demo-shaped stack under emulation; hit each health
#                       endpoint. Catches a broken build, not a behavioral difference.
#   8. Pin + tag        bump the three pins in dockerCompose/compose.production.yaml → commit → git tag
#                       v$VERSION → push the tag. The tag is what the Droplet deploys; it must carry
#                       the bumped pins (verified). The branch itself is NOT pushed — push it yourself.
#
# THERE IS NO FAST PATH and no skip flag, deliberately (notes §3.1): every release pays the full gate.
#
# PUBLISH=no — dry-run rehearsal, NOT a fast path (every gate still runs; it is the exits that close):
#   step 6 builds both platforms into the buildx cache instead of pushing (no manifest to verify),
#   step 7 smokes locally built emulated images (:$VERSION-smoke) instead of just-pushed ones,
#   step 8 is skipped entirely (no pin bump, no commit, no tag), and the preflight's clean-tree and
#   docker-login requirements soften to warnings. Nothing leaves the machine, git is not touched.
#   e.g.  PUBLISH=no ./scripts/release.sh 0.9.0-rc1 ../the-game-pensieve-web-v2 ../the-game-pensieve-mcp
#
# Conventions honoured (§3.5): arguments/env only, never prompts; set -euo pipefail; repo root resolved
# from this script's location; credentials come from `docker login` beforehand; non-zero exit on any
# failure with the last steps being verifications; re-runnable (a completed release refuses to rerun —
# the tag exists; a failed one can simply be rerun).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

HUB_USER="sethcondie"
API_IMAGE="$HUB_USER/the-game-pensieve-api"
WEB_IMAGE="$HUB_USER/the-game-pensieve-web"
MCP_IMAGE="$HUB_USER/the-game-pensieve-mcp"
PLATFORMS="linux/amd64,linux/arm64"
BUILDER="multiplatform"
JAR="target/the_game_pensieve_api.jar"
# Repo-relative on purpose: it is used as a git pathspec and in `git show <tag>:<path>` as well as on disk.
PROD_COMPOSE="dockerCompose/compose.production.yaml"
# Host ports for the step-7 smoke stack (odd on purpose — nothing else uses them).
SMOKE_API_PORT=19080
SMOKE_WEB_PORT=19200
SMOKE_MCP_PORT=19090

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# --- arguments ---------------------------------------------------------------------------------
VERSION="${1:-}"
WEB_REPO="${2:-}"
MCP_REPO="${3:-}"
[[ -n "$VERSION" && -n "$WEB_REPO" && -n "$MCP_REPO" ]] \
    || fail "usage: release.sh <version> <web-repo-path> <mcp-repo-path>   (env: PUBLISH=yes|no)"
PUBLISH="${PUBLISH:-yes}"
[[ "$PUBLISH" == "yes" || "$PUBLISH" == "no" ]] || fail "PUBLISH must be 'yes' or 'no' (got '$PUBLISH')"

# --- step bookkeeping --------------------------------------------------------------------------
CURRENT_STEP="(not started)"
STEP_SUMMARY=""
STEP_TS=0
step() {
    local now; now="$(date +%s)"
    if [[ "$STEP_TS" -ne 0 ]]; then
        STEP_SUMMARY+="$(printf '  %-28s %4ss' "$CURRENT_STEP" "$(( now - STEP_TS ))")"$'\n'
    fi
    CURRENT_STEP="$1"
    STEP_TS="$now"
    printf '\n=== [release] step: %s\n' "$1"
}

smoke_cleanup() {
    docker rm -f pensieve-smoke-db pensieve-smoke-api pensieve-smoke-web pensieve-smoke-mcp >/dev/null 2>&1 || true
    docker network rm pensieve-smoke >/dev/null 2>&1 || true
    # PUBLISH=no loads throwaway :$VERSION-smoke images for the emulated platform; drop them too.
    if [[ -n "${SMOKE_TAG:-}" && "${SMOKE_TAG:-}" != "$VERSION" ]]; then
        docker rmi -f "$API_IMAGE:$SMOKE_TAG" "$WEB_IMAGE:$SMOKE_TAG" "$MCP_IMAGE:$SMOKE_TAG" >/dev/null 2>&1 || true
    fi
}
on_exit() {
    local status=$?
    smoke_cleanup
    if [[ $status -ne 0 ]]; then
        printf '\n=== [release] FAILED (exit %s) during step: %s\n' "$status" "$CURRENT_STEP" >&2
    fi
    exit "$status"
}
trap on_exit EXIT

# ================================================================================================
step "1. preflight"
# ================================================================================================
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.]+)?$ ]] \
    || fail "version '$VERSION' is not X.Y.Z (optionally -suffix, e.g. 0.9.0-rc1)"
[[ -f "$WEB_REPO/playwright.config.ts" ]] || fail "'$WEB_REPO' does not look like the web repo"
[[ -f "$MCP_REPO/package.json" ]] || fail "'$MCP_REPO' does not look like the mcp repo"
command -v jq >/dev/null || fail "jq is required"
command -v curl >/dev/null || fail "curl is required"
command -v perl >/dev/null || fail "perl is required (portable in-place edit of the compose pins)"
docker info >/dev/null 2>&1 || fail "docker daemon is not running"
# Step 8 is the last thing that runs, ~30 minutes of gates after this point. Assert its target is where
# this script thinks it is now, rather than discovering a moved compose file at the very end.
[[ -f "$REPO_ROOT/$PROD_COMPOSE" ]] || fail "$PROD_COMPOSE not found — step 8 pins the images there"

# Clean working trees: the tag must describe exactly what was tested and shipped. Untracked files
# are tolerated (-uno) — localFiles/ scratch dirs are a working convention in these repos and do not
# reach any build — but a modified tracked file anywhere is a hard stop.
for repo in "$REPO_ROOT" "$WEB_REPO" "$MCP_REPO"; do
    if [[ -n "$(git -C "$repo" status --porcelain -uno)" ]]; then
        if [[ "$PUBLISH" == "yes" ]]; then
            fail "working tree not clean in $repo — commit or stash before releasing"
        fi
        printf 'WARNING: working tree not clean in %s (tolerated: PUBLISH=no)\n' "$repo"
    fi
done
[[ "$(git -C "$REPO_ROOT" branch --show-current)" == "master" ]] \
    || printf 'WARNING: releasing from branch %s, not master\n' "$(git -C "$REPO_ROOT" branch --show-current)"

# A finished release is not re-runnable — that is the re-run safety: it refuses, changes nothing.
[[ -z "$(git -C "$REPO_ROOT" tag -l "v$VERSION")" ]] \
    || fail "tag v$VERSION already exists — this version has been released"

docker buildx inspect "$BUILDER" >/dev/null 2>&1 \
    || fail "buildx builder '$BUILDER' not found — one-time setup: docker buildx create --name $BUILDER --use && docker buildx inspect --bootstrap"
if ! docker login </dev/null >/dev/null 2>&1; then
    [[ "$PUBLISH" == "yes" ]] \
        && fail "docker login is not live — run 'docker login' (with an access token) first"
    printf 'WARNING: docker login is not live (tolerated: PUBLISH=no)\n'
fi

for port in "$SMOKE_API_PORT" "$SMOKE_WEB_PORT" "$SMOKE_MCP_PORT"; do
    if (echo >"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then
        fail "smoke port $port is already in use"
    fi
done

# The suite (steps 2-5) only ever runs host-platform binaries; the OTHER published platform ships
# suite-untested, so that is the one step 7 smokes under emulation.
case "$(docker info --format '{{.Architecture}}')" in
    aarch64|arm64) SMOKE_PLATFORM="linux/amd64" ;;
    *)             SMOKE_PLATFORM="linux/arm64" ;;
esac
printf 'preflight ok: version %s, smoke platform %s\n' "$VERSION" "$SMOKE_PLATFORM"

# ================================================================================================
step "2. unit gates"
# ================================================================================================
# api: 45 test classes, both auth modes (mode is a per-class annotation); provisions its own
# Testcontainers (throwaway env #1 of 3). Checkstyle runs automatically (bound to validate).
(cd "$REPO_ROOT" && ./mvnw test)
(cd "$WEB_REPO" && CI=1 npm test)
(cd "$MCP_REPO" && CI=1 npm test)

# ================================================================================================
step "3. build local images (host arch)"
# ================================================================================================
# Single-arch builds for the gates. The multi-arch publish (step 6) rebuilds — the pushed artifact
# is not byte-identical to the gated one; a known, accepted gap (notes §3.1).
(cd "$REPO_ROOT" && ./mvnw install -DskipTests -q)
docker build --build-arg JAR_FILE="$JAR" -t "$API_IMAGE:$VERSION" "$REPO_ROOT"
docker build -t "$WEB_IMAGE:$VERSION" "$WEB_REPO"
docker build -t "$MCP_IMAGE:$VERSION" "$MCP_REPO"

# ================================================================================================
step "4. gate A: secured e2e"
# ================================================================================================
PENSIEVE_TAG="$VERSION" "$SCRIPT_DIR/e2e-gate.sh" secured "$WEB_REPO"

# ================================================================================================
step "5. gate B: demo e2e"
# ================================================================================================
PENSIEVE_TAG="$VERSION" "$SCRIPT_DIR/e2e-gate.sh" demo "$WEB_REPO"

# ================================================================================================
step "6. publish multi-arch + verify manifests"
# ================================================================================================
# :$VERSION is immutable, :latest moves with each release (§3.6) — both pushed in one invocation.
# PUBLISH=no still builds BOTH platforms (a broken arm64/amd64 build fails right here) but the
# result stays in the buildx cache: nothing is pushed, so there is no registry manifest to verify.
if [[ "$PUBLISH" == "yes" ]]; then OUTPUT=(--push); else OUTPUT=(--output type=cacheonly); fi
docker buildx build --builder "$BUILDER" --platform "$PLATFORMS" \
    --build-arg JAR_FILE="$JAR" \
    -t "$API_IMAGE:$VERSION" -t "$API_IMAGE:latest" "${OUTPUT[@]}" "$REPO_ROOT"
docker buildx build --builder "$BUILDER" --platform "$PLATFORMS" \
    -t "$WEB_IMAGE:$VERSION" -t "$WEB_IMAGE:latest" "${OUTPUT[@]}" "$WEB_REPO"
docker buildx build --builder "$BUILDER" --platform "$PLATFORMS" \
    -t "$MCP_IMAGE:$VERSION" -t "$MCP_IMAGE:latest" "${OUTPUT[@]}" "$MCP_REPO"

# Verification is not optional: assert both platforms are actually in each pushed manifest, and that
# :latest now points at the same digest as :$VERSION.
verify_manifest() {
    local image="$1" platforms digest_v digest_l
    platforms="$(docker buildx imagetools inspect --raw "$image:$VERSION" \
        | jq -r '[.manifests[]?.platform | select(. != null) | "\(.os)/\(.architecture)"] | unique | join(",")')"
    [[ "$platforms" == *"linux/amd64"* && "$platforms" == *"linux/arm64"* ]] \
        || fail "$image:$VERSION manifest is missing a platform (got: '$platforms') — the multi-arch push did not take"
    digest_v="$(docker buildx imagetools inspect --format '{{.Manifest.Digest}}' "$image:$VERSION")"
    digest_l="$(docker buildx imagetools inspect --format '{{.Manifest.Digest}}' "$image:latest")"
    [[ "$digest_v" == "$digest_l" ]] \
        || fail "$image:latest ($digest_l) did not move to the $VERSION digest ($digest_v)"
    printf 'verified %s:%s — %s, :latest moved\n' "$image" "$VERSION" "$platforms"
}
if [[ "$PUBLISH" == "yes" ]]; then
    verify_manifest "$API_IMAGE"
    verify_manifest "$WEB_IMAGE"
    verify_manifest "$MCP_IMAGE"
else
    printf 'PUBLISH=no: both platforms built to cache; nothing pushed, manifest verification skipped\n'
fi

# ================================================================================================
step "7. $SMOKE_PLATFORM smoke (emulated)"
# ================================================================================================
# A demo-shaped mini stack of the non-host-platform images: db (host arch — not our artifact) +
# backend + frontend + mcp. Each answers its health endpoint or the release fails.
# PUBLISH=yes smokes the images JUST PUSHED to the registry (--pull always); PUBLISH=no loads the
# same builds from the step-6 buildx cache under a :$VERSION-smoke tag so the host-arch :$VERSION
# images from step 3 are left untouched.
# Clean leftovers FIRST — smoke_cleanup also removes :$VERSION-smoke tags, so it must never run
# between building the smoke images and running them.
smoke_cleanup
if [[ "$PUBLISH" == "yes" ]]; then
    SMOKE_TAG="$VERSION"
    PULL_ARGS=(--pull always)
else
    SMOKE_TAG="$VERSION-smoke"
    PULL_ARGS=(--pull never)
    docker buildx build --builder "$BUILDER" --platform "$SMOKE_PLATFORM" \
        --build-arg JAR_FILE="$JAR" -t "$API_IMAGE:$SMOKE_TAG" --load "$REPO_ROOT"
    docker buildx build --builder "$BUILDER" --platform "$SMOKE_PLATFORM" \
        -t "$WEB_IMAGE:$SMOKE_TAG" --load "$WEB_REPO"
    docker buildx build --builder "$BUILDER" --platform "$SMOKE_PLATFORM" \
        -t "$MCP_IMAGE:$SMOKE_TAG" --load "$MCP_REPO"
fi
docker network create pensieve-smoke >/dev/null
docker run -d --name pensieve-smoke-db --network pensieve-smoke --network-alias db \
    -e POSTGRES_PASSWORD=root -e POSTGRES_DB=pensieve-db postgres:16.2-alpine >/dev/null
docker run -d --name pensieve-smoke-api --network pensieve-smoke --network-alias backend \
    --platform "$SMOKE_PLATFORM" "${PULL_ARGS[@]}" -p "127.0.0.1:$SMOKE_API_PORT:8080" \
    -e SPRING_PROFILES_ACTIVE=docker "$API_IMAGE:$SMOKE_TAG" >/dev/null
docker run -d --name pensieve-smoke-web --network pensieve-smoke \
    --platform "$SMOKE_PLATFORM" "${PULL_ARGS[@]}" -p "127.0.0.1:$SMOKE_WEB_PORT:3000" \
    -e API_BASE_URL=http://backend:8080/v1 "$WEB_IMAGE:$SMOKE_TAG" >/dev/null
docker run -d --name pensieve-smoke-mcp --network pensieve-smoke \
    --platform "$SMOKE_PLATFORM" "${PULL_ARGS[@]}" -p "127.0.0.1:$SMOKE_MCP_PORT:3000" \
    -e API_BASE_URL=http://backend:8080/v1 -e PORT=3000 "$MCP_IMAGE:$SMOKE_TAG" >/dev/null

smoke_wait() { # DESCRIPTION TIMEOUT URL [JQ_FILTER]
    local desc="$1" timeout="$2" url="$3" filter="${4:-.}" deadline
    deadline=$(( $(date +%s) + timeout ))
    until curl -fsS -m 3 "$url" 2>/dev/null | jq -e "$filter" >/dev/null 2>&1; do
        if (( $(date +%s) >= deadline )); then
            printf '\n--- smoke container logs ---\n'
            docker logs --tail 25 pensieve-smoke-api 2>&1 || true
            fail "smoke: timed out after ${timeout}s waiting for $desc ($url)"
        fi
        sleep 3
    done
    printf 'smoke ok: %s\n' "$desc"
}
# JVM boot + Flyway under emulation is the slow one; the Node images follow quickly.
smoke_wait "backend /v1/heartbeat" 300 "http://127.0.0.1:$SMOKE_API_PORT/v1/heartbeat" '.data.secureMode == false'
smoke_wait "frontend /api/heartbeat" 120 "http://127.0.0.1:$SMOKE_WEB_PORT/api/heartbeat" '.status == "online"'
smoke_wait "mcp /healthz" 60 "http://127.0.0.1:$SMOKE_MCP_PORT/healthz"
smoke_cleanup

# ================================================================================================
step "8. pin $PROD_COMPOSE + tag"
# ================================================================================================
cd "$REPO_ROOT"
if [[ "$PUBLISH" == "no" ]]; then
    printf 'PUBLISH=no: skipped — would bump the three %s pins to :%s,\n' "$PROD_COMPOSE" "$VERSION"
    printf '            commit, tag v%s (verifying the tag carries the pins), and push the tag.\n' "$VERSION"
    step "done"
    printf '\n=== [release] %s dry run complete — NOTHING was published or tagged ===\n' "$VERSION"
    printf 'step timings:\n%s' "$STEP_SUMMARY"
    printf '\nrun it for real: ./scripts/release.sh %s <web-repo> <mcp-repo>   (PUBLISH defaults to yes)\n' "$VERSION"
    exit 0
fi
perl -pi -e "s#(image: \Q$HUB_USER\E/the-game-pensieve-(?:api|web|mcp)):\S+#\${1}:$VERSION#" "$PROD_COMPOSE"
[[ "$(grep -c "image: $HUB_USER/the-game-pensieve-.*:$VERSION" "$PROD_COMPOSE")" == "3" ]] \
    || fail "pin bump failed — $PROD_COMPOSE does not carry three :$VERSION pins"
if ! git diff --quiet -- "$PROD_COMPOSE"; then
    git add "$PROD_COMPOSE"
    git commit -m "Release $VERSION — pin production images"
fi
git tag -a "v$VERSION" -m "Release $VERSION"
# The tag is what the Droplet checks out; verify it carries the pins before pushing it anywhere.
[[ "$(git show "v$VERSION:$PROD_COMPOSE" | grep -c ":$VERSION")" == "3" ]] \
    || fail "tag v$VERSION does not contain the bumped pins"
git push origin "v$VERSION"

# ================================================================================================
step "done"
# ================================================================================================
printf '\n=== [release] %s published and tagged ===\n' "$VERSION"
printf 'step timings:\n%s' "$STEP_SUMMARY"
printf '\nimages:  %s:%s  %s:%s  %s:%s  (+ :latest moved)\n' \
    "$API_IMAGE" "$VERSION" "$WEB_IMAGE" "$VERSION" "$MCP_IMAGE" "$VERSION"
printf 'tag:     v%s pushed (the release branch itself was NOT pushed — push it when ready)\n' "$VERSION"
printf 'deploy:  make deploy VERSION=%s   (once Pipeline B exists)\n' "$VERSION"
