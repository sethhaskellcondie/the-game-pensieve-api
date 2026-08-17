#!/usr/bin/env bash
#
# DEPLOY WRAPPER — preflight locally, then hand off to the Droplet in one SSH call.
#
#   ./scripts/deploy-production.sh <version>          e.g.  make deploy VERSION=1.0.0
#   DRY_RUN=yes ./scripts/deploy-production.sh 1.0.0  # rehearse: every check runs, nothing changes
#
# WHAT THIS HALF IS FOR: failing in seconds, before production is touched. Every check below costs
# nothing and catches a mistake that would otherwise surface minutes into the deploy — or worse, as a
# half-deployed stack. The real work happens in scripts/deploy-production-remote.sh, which lives in
# this repo so it is versioned in the SAME COMMIT as dockerCompose/compose.production.yaml, the
# Caddyfile, and the realm import: the four artifacts can never drift apart. The remote script reads
# dockerCompose/.env on the box, so no secret ever crosses the wire.
#
# PREFLIGHT (all local, all fast):
#   1. version shape is X.Y.Z (optionally -suffix); `latest` is rejected EXPLICITLY — a moving tag
#      never deploys to production
#   2. the git tag v$VERSION exists on origin — the Droplet deploys by checking that tag out
#   3. the tag CONTAINS scripts/deploy-production-remote.sh — found by the 1.0.0 rollback test
#      (2026-08-17): a tag cut before the deploy pipeline existed has no remote script, so the
#      bootstrap's checkout deletes the very file it is about to exec, leaving the Droplet checkout
#      moved with nothing deployed; such versions can only be deployed/rolled back by hand
#   4. all three images exist on Docker Hub at :$VERSION and their manifests carry linux/amd64 —
#      deploying a version whose frontend was never pushed is the single most likely mistake, and a
#      single-arch arm64 push is invisible until the amd64 Droplet pulls it
#   5. the deploy host answers over SSH (BatchMode — a prompt would mean a CI runner hangs forever)
#
# THE HANDOFF (§4.4 of the pipeline doc — the sequencing is load-bearing): bash reads a script file
# incrementally while executing it, so the remote script must NEVER `git checkout` over itself. The
# bootstrap below is a tiny inline command that checks out v$VERSION FIRST and only then `exec bash`es
# the remote script — opening the settled, new file fresh. Do not "simplify" it into the remote script.
#
# WHAT A GREEN DEPLOY DOES NOT PROVE: that login works end to end (nothing here drives a browser),
# that email is delivered to an inbox, or that the realm's one-shot import matches the repo (it ran
# once, on the FIRST deploy, and never again — see keycloak/README.md). The health checks prove the
# chain serves and the backend is secured; the first-deploy checklist in compose.production.yaml's
# header is what proves the rest, once, by hand.
#
# ROLLBACK IS THIS SAME SCRIPT with the previous version tag — there is deliberately no rollback.sh; a
# second code path exercised only during an emergency is worse than none. Note migrations are
# forward-only (additive by convention), so an image rollback never rolls back the schema.
#
# Inputs (arguments and environment only — never prompts; conventions §3.5):
#   $1            version to deploy (required; must exist as tag v$VERSION and on Docker Hub)
#   DEPLOY_HOST   ssh destination (default: pensieve-prod — define it as a Host alias in ~/.ssh/config)
#   DRY_RUN=yes   run every preflight, then have the remote side rehearse read-only too: it fetches
#                 tags but does NOT check out, back up, pull, or restart anything. With no reachable
#                 host the SSH check softens to a warning and the bootstrap is printed instead of run.
#
# The workstation needs: git, docker (logged in enough to inspect public manifests), ssh.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

HUB_USER="sethcondie"
IMAGES=("$HUB_USER/the-game-pensieve-api" "$HUB_USER/the-game-pensieve-web" "$HUB_USER/the-game-pensieve-mcp")
DEPLOY_HOST="${DEPLOY_HOST:-pensieve-prod}"
DEPLOY_PATH="/opt/pensieve"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
note() { printf '[deploy] %s\n' "$*"; }

VERSION="${1:-}"
[[ -n "$VERSION" ]] || fail "usage: deploy-production.sh <version>   (env: DEPLOY_HOST, DRY_RUN=yes|no)"
DRY_RUN="${DRY_RUN:-no}"
[[ "$DRY_RUN" == "yes" || "$DRY_RUN" == "no" ]] || fail "DRY_RUN must be 'yes' or 'no' (got '$DRY_RUN')"

# --- 1. version shape ---------------------------------------------------------------------------
# `latest` gets its own message before the regex: it is not a malformed version, it is a category
# error, and the refusal should say so.
[[ "$VERSION" != "latest" ]] || fail "refusing to deploy 'latest' — production deploys immutable version tags only"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.]+)?$ ]] \
    || fail "version '$VERSION' is not X.Y.Z (optionally -suffix, e.g. 1.0.1-rc1)"
note "preflight 1/5: version shape ok ($VERSION)"

# --- 2. the tag exists on origin ----------------------------------------------------------------
[[ -n "$(git -C "$REPO_ROOT" ls-remote --tags origin "refs/tags/v$VERSION")" ]] \
    || fail "tag v$VERSION does not exist on origin — release it first (make release VERSION=$VERSION)"
note "preflight 2/5: tag v$VERSION exists on origin"

# --- 3. the tag carries the remote deploy script ------------------------------------------------
# The bootstrap checks the tag out and THEN execs scripts/deploy-production-remote.sh from it; a tag
# that predates the deploy pipeline (v1.0.0) has no such file, and the checkout would delete the
# script mid-handoff — discovered by the deliberate 1.0.0 rollback test. Fetch tags first so the
# content check inspects the same ref the Droplet will.
git -C "$REPO_ROOT" fetch --tags --quiet origin
git -C "$REPO_ROOT" cat-file -e "refs/tags/v$VERSION:scripts/deploy-production-remote.sh" 2>/dev/null \
    || fail "tag v$VERSION does not contain scripts/deploy-production-remote.sh — it predates the deploy pipeline and can only be deployed/rolled back by hand (see buildFromScratch.md)"
note "preflight 3/5: tag v$VERSION carries the remote deploy script"

# --- 4. all three images exist on Docker Hub, and carry linux/amd64 -----------------------------
docker info >/dev/null 2>&1 || fail "docker daemon is not running (needed to inspect the Hub manifests)"
for image in "${IMAGES[@]}"; do
    manifest="$(docker buildx imagetools inspect "$image:$VERSION" 2>&1)" \
        || fail "$image:$VERSION not found on Docker Hub — was this version released? ($manifest)"
    grep -q 'linux/amd64' <<<"$manifest" \
        || fail "$image:$VERSION exists but its manifest has no linux/amd64 entry — the Droplet cannot run it"
done
note "preflight 4/5: all three images on Docker Hub at :$VERSION with linux/amd64"

# --- 5. the host answers ------------------------------------------------------------------------
if ssh -o BatchMode=yes -o ConnectTimeout=10 "$DEPLOY_HOST" true 2>/dev/null; then
    note "preflight 5/5: $DEPLOY_HOST reachable over SSH"
    HOST_REACHABLE=yes
else
    [[ "$DRY_RUN" == "yes" ]] \
        || fail "$DEPLOY_HOST is not reachable over SSH (define a Host alias in ~/.ssh/config, or set DEPLOY_HOST)"
    note "preflight 5/5: WARNING — $DEPLOY_HOST not reachable; tolerated because DRY_RUN=yes"
    HOST_REACHABLE=no
fi

# --- the one SSH call ---------------------------------------------------------------------------
# Live: fetch, check out the tag, and only then exec the (now settled) remote script — §4.4.
# Dry run: fetch tags but do NOT move the checkout; the remote script rehearses read-only from
# whatever revision the Droplet already has, verifying the tag's content via `git show`.
if [[ "$DRY_RUN" == "yes" ]]; then
    BOOTSTRAP="cd $DEPLOY_PATH \
    && git fetch --tags --prune \
    && DRY_RUN=yes exec bash scripts/deploy-production-remote.sh '$VERSION'"
else
    BOOTSTRAP="cd $DEPLOY_PATH \
    && git fetch --tags --prune \
    && git checkout --detach 'v$VERSION' \
    && DRY_RUN=no exec bash scripts/deploy-production-remote.sh '$VERSION'"
fi

if [[ "$DRY_RUN" == "yes" && "$HOST_REACHABLE" == "no" ]]; then
    printf '\n[deploy] DRY_RUN with no reachable host — this is the call a live run would make:\n'
    printf '  ssh %s "%s"\n' "$DEPLOY_HOST" "$BOOTSTRAP"
    printf '[deploy] dry run complete — nothing was changed anywhere.\n'
    exit 0
fi

note "handing off to $DEPLOY_HOST (DRY_RUN=$DRY_RUN)"
exec ssh "$DEPLOY_HOST" "$BOOTSTRAP"
