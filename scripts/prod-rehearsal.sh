#!/usr/bin/env bash
#
# PRODUCTION REHEARSAL — run the real hosted stack on this workstation and verify it, then tear it down.
#
#   ./scripts/prod-rehearsal.sh [env-file]        # default: dockerCompose/.env.rehearsal
#
# WHAT MAKES THIS A REAL REHEARSAL: it runs `dockerCompose/compose.production.yaml` and `Caddyfile`
# **unmodified** —
# the same files, the same topology, the same one-shot realm import, real TLS. Only the `.env` differs,
# and only in its three domains. Three facts make that possible:
#
#   1. `*.localhost` resolves to 127.0.0.1 natively on macOS and Linux — no /etc/hosts entry needed.
#   2. Caddy will not attempt ACME for a `.localhost` name; it issues from its own internal CA instead.
#      So the Caddyfile needs no dry-run switch, and every check below validates a real certificate
#      chain (--cacert, never -k) exactly as production will against Let's Encrypt.
#   3. No container ever needs to resolve the public hostnames. The web BFF uses OIDC_INTERNAL_ISSUER
#      for server-side token exchange and only compares `iss` as a string (web repo src/lib/oidc.ts);
#      the sidecar and backend fetch JWKS over keycloak:8080. Only your browser and this script's curl
#      touch the public names.
#
# WHAT IT PROVES (see the CHECKS section for the full list):
#   • dockerCompose/compose.production.yaml actually comes up — it has never been run anywhere (plan §1.2)
#   • the one-shot Keycloak realm import resolves all ten ${PENSIEVE_*} placeholders (hazard §4.2)
#   • the web client secret, redirect URIs, and the audience mapper agree with the sidecar's OAuth config
#   • the backend is in secured mode and publishes no host port — a dropped `secured` profile (audit B3)
#     shows up here as a named failure, not as a silent fail-open in production
#   • there is NO basic-auth gate anywhere on the auth host (removed 2026-08-18 — four rounds of it
#     breaking the console; see the Caddyfile's auth-host comment): the console page, the Admin REST
#     API, the master token endpoint, and the console runtime API must all answer WITHOUT a Basic
#     challenge, and the login pages and /resources/ assets stay public
#   • anonymous dynamic client registration is refused
#   • Flyway migrated a clean production-shaped database
#   • A REAL LOGIN COMPLETES. A throwaway user is created and the authorization-code + PKCE flow is
#     driven all the way through — /api/auth/login, the Keycloak form, the callback's code exchange —
#     ending in a session whose role came from the backend. That last part is the point: the callback
#     can only report a role after GET /v1/auth/me succeeds, so the secured backend has accepted the
#     token's `aud` and `iss`. The realm's audience mapper, the sidecar's config, and the backend's
#     validator are proven to agree, not just inspected. The probe user is deleted afterwards.
#
# WHAT IT CANNOT PROVE — do not mistake a green run for these (Tier 3, a throwaway Droplet, covers the
# first three):
#   • ACME / Let's Encrypt against real DNS      • the linux/amd64 images (this runs host arch)
#   • Droplet sizing, swap, and firewall         • real inbox deliverability (SPF/DKIM)
#   • how any of it LOOKS. Nothing renders a page. Re-run with KEEP_STACK=1 CREATE_TEST_USER=1, trust
#     the printed Caddy root, and browse it yourself.
#
# ONE-SHOT IMPORT, ONE-SHOT VOLUMES. Keycloak imports the realm only on first boot with an empty
# keycloak-db, so this script ALWAYS starts with `down -v`. A stale volume would skip the import and
# quietly invalidate the entire rehearsal. The `-p pensieve-prod-rehearsal` below overrides the compose
# file's own pinned `name: pensieve`, and is distinct from the dev stack (`the-game-pensieve-api`) and the
# e2e gate (`pensieve-e2e`), so no volume here can ever be the one production starts from. That override is
# load-bearing: without -p this script's `down -v` would target the real production project name.
#
# FAIL-FAST vs COLLECT. Readiness waits fail fast — nothing downstream can pass if the stack is not up.
# The checks then all run and report together, deliberately: a rehearsal costs several minutes of boot,
# so you want every problem in one pass, not one per iteration. Exit is non-zero if any check failed.
#
# Inputs (arguments and environment only — never prompts; conventions §3.5):
#   $1                      env file                        (default: dockerCompose/.env.rehearsal)
#   KEEP_STACK=1            skip teardown, print the browser follow-ups and how to tear down later
#   CREATE_TEST_USER=1      create a login-able user in the pensieve realm (implies the stack is kept)
#   ALLOW_PUBLIC_DOMAINS=1  permit non-.localhost domains — required for a Tier 3 staging host, where
#                           Caddy WILL hit the real ACME endpoint. Off by default so a stray copy of
#                           the production .env cannot make this script serve, or request certs for,
#                           the real domains.
#   SMTP_TEST_TO=addr       run the Phase B0 email check: send Keycloak's test message to this address
#
# The host needs: docker, curl, jq, openssl, and ports 80 and 443 free.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

PROJECT="pensieve-prod-rehearsal"
COMPOSE_FILE="$REPO_ROOT/dockerCompose/compose.production.yaml"
ENV_FILE_ARG="${1:-dockerCompose/.env.rehearsal}"
WORK_DIR="$(mktemp -d)"
CA_CERT="$WORK_DIR/caddy-root.crt"

START_TS="$(date +%s)"
log()  { printf '\n=== [rehearsal] %s (t+%ss)\n' "$*" "$(( $(date +%s) - START_TS ))"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# The bind mounts (Caddyfile, keycloak/import-prod) are relative to dockerCompose/, so compose resolves them
# identically from any working directory — the §4.3 hazard is closed at the source. The cd is kept so that a
# relative env-file argument is interpreted against the repo root, which is what the default assumes.
cd "$REPO_ROOT"
ENV_FILE="$(cd "$(dirname "$ENV_FILE_ARG")" && pwd)/$(basename "$ENV_FILE_ARG")"
compose() { docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"; }

# ================================================================================================
log "preflight"
# ================================================================================================
for tool in docker curl jq openssl; do
    command -v "$tool" >/dev/null || fail "$tool is required"
done
docker info >/dev/null 2>&1 || fail "docker daemon is not running"
[[ -f "$COMPOSE_FILE" ]]                 || fail "missing $COMPOSE_FILE"
[[ -f "$REPO_ROOT/Caddyfile" ]]          || fail "missing Caddyfile (compose bind-mounts it)"
[[ -f "$REPO_ROOT/keycloak/import-prod/pensieve-realm.json" ]] \
                                         || fail "missing keycloak/import-prod/pensieve-realm.json"

# Caddy binds 80 and 443 on the host; production's edge cannot be port-remapped without changing the
# artifact under test, so these two must genuinely be free.
for port in 80 443; do
    if (echo >"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then
        fail "port $port is in use — Caddy needs 80 and 443 (leftover stack? 'docker compose -p $PROJECT -f $COMPOSE_FILE down -v')"
    fi
done

# --- the env file ---------------------------------------------------------------------------------
# Generated on first run: throwaway secrets, .localhost domains, and the SMTP block left as the
# example's placeholders. That is a full TOPOLOGY rehearsal. Phase B0's email half additionally needs
# the REAL relay credentials edited in — see the closing notes.
if [[ ! -f "$ENV_FILE" ]]; then
    log "generating $ENV_FILE (throwaway secrets, .localhost domains)"
    cat > "$ENV_FILE" <<EOF
# Generated by scripts/prod-rehearsal.sh — throwaway values for a LOCAL rehearsal only.
# Covered by .gitignore (.env.*). Never reuse any secret in this file on the Droplet.

APP_DOMAIN=pensieve.localhost
MCP_DOMAIN=mcp.localhost
AUTH_DOMAIN=auth.localhost
ACME_EMAIL=rehearsal@example.com

SESSION_SECRET=$(openssl rand -base64 48)
OIDC_CLIENT_SECRET=$(openssl rand -base64 32)
POSTGRES_PASSWORD=$(openssl rand -base64 24)
KC_DB_PASSWORD=$(openssl rand -base64 24)
KC_ADMIN_USER=admin
KC_ADMIN_PASSWORD=$(openssl rand -base64 24)

# PLACEHOLDERS. Phase B0 (the email half of the rehearsal) needs the real relay values here,
# plus SMTP_TEST_TO=<your inbox> on the command line: replace SMTP_FROM with the address on the domain
# verified with Resend, and set SMTP_PASSWORD to the API key. SMTP_FROM is left as a placeholder rather
# than blank so the realm import still gets a well-formed address on the topology-only run -- but a
# placeholder From is rejected by the relay AFTER a successful authentication, which is a confusing way
# to fail, so change it before setting SMTP_TEST_TO.
# Port and TLS flags are a PAIR: 465/2465 -> STARTTLS=false, SSL=true; 587/2587 -> STARTTLS=true,
# SSL=false. 2465 matches production, where DigitalOcean blocks outbound 25/465/587.
SMTP_HOST=smtp.resend.com
SMTP_PORT=2465
SMTP_FROM=no-reply@pensieve.example.com
SMTP_USER=resend
SMTP_PASSWORD=
SMTP_STARTTLS=false
SMTP_SSL=true
EOF
    chmod 600 "$ENV_FILE"
fi

# Sourcing (rather than parsing) keeps this script's view of the values identical to compose's: shell
# environment takes precedence over --env-file during interpolation, so exporting them here guarantees
# the stack and the checks are talking about the same domains and secrets.
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

for required in APP_DOMAIN MCP_DOMAIN AUTH_DOMAIN SESSION_SECRET OIDC_CLIENT_SECRET \
                POSTGRES_PASSWORD KC_DB_PASSWORD KC_ADMIN_USER KC_ADMIN_PASSWORD; do
    [[ -n "${!required:-}" ]] || fail "$required is empty in $ENV_FILE"
done
# Audit B4: an empty SESSION_SECRET silently falls back to the committed dev secret in the web BFF.
(( ${#SESSION_SECRET} >= 32 )) || fail "SESSION_SECRET must be >= 32 chars (got ${#SESSION_SECRET})"

if [[ "${ALLOW_PUBLIC_DOMAINS:-0}" != "1" ]]; then
    # APP_ALIAS_DOMAIN is optional and usually unset here — compose then supplies alias.localhost, which
    # passes this check like any other .localhost name. It is included so that setting it to a real typo
    # host cannot smuggle a public name past the guard the other three are held to.
    for domain in "$APP_DOMAIN" "$MCP_DOMAIN" "$AUTH_DOMAIN" "${APP_ALIAS_DOMAIN:-alias.localhost}"; do
        [[ "$domain" == *.localhost ]] \
            || fail "domain '$domain' is not a .localhost name. A public domain makes Caddy hit the REAL ACME endpoint and serve the real hostnames — set ALLOW_PUBLIC_DOMAINS=1 only if that is what you want (Tier 3 staging host)."
    done
fi

# The pins in the compose file are the artifact under test; do not override them. Warn if they
# are still :latest — deploy-production.sh will reject that, but a rehearsal of it is legitimate.
PINS=()
while IFS= read -r pin; do PINS+=("$pin"); done < <(
    grep -oE 'image: sethcondie/the-game-pensieve-[a-z]+:[^ ]+' "$COMPOSE_FILE" | awk '{print $2}')
(( ${#PINS[@]} == 3 )) || fail "expected 3 pensieve image pins in $COMPOSE_FILE, found ${#PINS[@]}"
for pin in "${PINS[@]}"; do
    docker image inspect "$pin" >/dev/null 2>&1 || {
        printf 'pulling %s (not in the local image store)\n' "$pin"
        docker pull "$pin" >/dev/null || fail "cannot pull $pin — build it locally or run the release first"
    }
    if [[ "$pin" == *:latest ]]; then
        printf 'NOTE: %s is pinned to :latest — production deploys a version tag (§4.8)\n' "$pin"
    fi
done
printf 'preflight ok: %s | %s | %s | images: %s\n' "$APP_DOMAIN" "$MCP_DOMAIN" "$AUTH_DOMAIN" "${PINS[*]}"

# ================================================================================================
log "clean start (destroying any previous rehearsal volumes)"
# ================================================================================================
# Non-negotiable: the realm import runs ONLY on first boot with an empty keycloak-db (hazard §4.2).
compose down -v --remove-orphans >/dev/null 2>&1 || true

KEEP="${KEEP_STACK:-0}"
# A test user exists to be logged in with, by hand — so creating one implies keeping the stack.
if [[ "${CREATE_TEST_USER:-0}" == "1" ]]; then KEEP=1; fi
teardown() {
    status=$?
    if [[ $status -ne 0 && "${CHECKS_RUN:-0}" == "0" ]]; then
        printf '\n=== [rehearsal] FAILED before the checks ran (exit %s) — last container logs ===\n' "$status"
        compose logs --tail=40 || true
    fi
    if [[ "$KEEP" == "1" ]]; then
        printf '\n=== [rehearsal] stack left running under project %s\n' "$PROJECT"
        printf '    tear down with: docker compose -p %s --env-file %s -f %s down -v\n' \
            "$PROJECT" "$ENV_FILE" "$COMPOSE_FILE"
        printf '    (the -v matters: the next run needs an empty keycloak-db to re-import the realm)\n'
        printf '    Caddy root CA: %s\n' "$CA_CERT"
    else
        compose down -v --remove-orphans >/dev/null 2>&1 || true
        rm -rf "$WORK_DIR"
    fi
    exit "$status"
}
trap teardown EXIT

log "starting the production stack (project: $PROJECT)"
compose up -d --remove-orphans

# ================================================================================================
log "readiness"
# ================================================================================================
# depends_on waits for container start, not readiness; every wait below is a real check. Production
# runs on real hardware with a cold pull, so these are the generous end of the §4.7 table.
wait_for() { # wait_for DESCRIPTION TIMEOUT CMD...
    local desc="$1" timeout="$2" deadline
    shift 2
    deadline=$(( $(date +%s) + timeout ))
    until "$@" >/dev/null 2>&1; do
        if (( $(date +%s) >= deadline )); then
            printf '\n--- last logs ---\n'
            compose logs --tail=40 || true
            fail "timed out after ${timeout}s waiting for: $desc"
        fi
        sleep 3
    done
    printf 'ready: %s\n' "$desc"
}

# Caddy mints its internal CA when it first loads a config needing a certificate. Everything after this
# validates against that root, so a check that reaches a service has also proved its TLS chain.
extract_ca() { compose cp caddy:/data/caddy/pki/authorities/local/root.crt "$CA_CERT" 2>/dev/null && [[ -s "$CA_CERT" ]]; }
wait_for "caddy internal CA issued" 90 extract_ca

CURL=(curl -sS -m 15 --cacert "$CA_CERT")
get_json()  { "${CURL[@]}" -f "$@"; }
http_code() { "${CURL[@]}" -o /dev/null -w '%{http_code}' "$@"; }

r_db()       { compose exec -T db pg_isready -U postgres -d pensieve-db; }
r_kcdb()     { compose exec -T keycloak-db pg_isready -U keycloak -d keycloak; }
r_realm()    { get_json "https://$AUTH_DOMAIN/realms/pensieve" | jq -e '.realm == "pensieve"'; }
r_app()      { get_json "https://$APP_DOMAIN/api/heartbeat" | jq -e '.status == "online"'; }
r_mcp()      { get_json "https://$MCP_DOMAIN/healthz" | jq -e '.status == "ok"'; }

wait_for "app postgres accepting connections"      90  r_db
wait_for "keycloak postgres accepting connections" 90  r_kcdb
# First boot imports the realm (30-60s) on top of Keycloak's own start.
wait_for "keycloak realm 'pensieve' over TLS"      300 r_realm
# First boot runs every Flyway migration; the JVM is the slow part after that. This is the app chain:
# Caddy -> TLS -> frontend -> private network -> backend (§4.6).
wait_for "app chain https://$APP_DOMAIN/api/heartbeat" 300 r_app
wait_for "mcp sidecar /healthz"                    120 r_mcp

# ================================================================================================
log "checks"
# ================================================================================================
CHECKS_RUN=1
PASSED=0; FAILED=0; SKIPPED=0; FAILED_NAMES=""
check() { # check NAME CMD...
    local name="$1" out; shift
    if out="$("$@" 2>&1)"; then
        printf '  PASS  %s\n' "$name"
        PASSED=$((PASSED + 1))
    else
        printf '  FAIL  %s\n' "$name"
        FAILED=$((FAILED + 1))
        FAILED_NAMES+="    - $name"$'\n'
    fi
    [[ -n "$out" ]] && printf '        %s\n' "$out" | sed '2,$s/^/        /'
    return 0
}
skip() { printf '  SKIP  %s\n' "$1"; printf '        %s\n' "$2"; SKIPPED=$((SKIPPED + 1)); }

expect_code() { # expect_code EXPECTED_REGEX URL [curl args...]
    local expected="$1" url="$2" code; shift 2
    code="$(http_code "$@" "$url")"
    [[ "$code" =~ $expected ]] || { echo "$url -> HTTP $code (expected $expected)"; return 1; }
    echo "HTTP $code"
}
expect_json() { # expect_json URL JQ_FILTER
    local url="$1" filter="$2" body
    body="$(get_json "$url")" || { echo "request failed: $url"; return 1; }
    jq -e "$filter" >/dev/null 2>&1 <<<"$body" \
        || { echo "$url"; echo "expected: $filter"; echo "actual: $(head -c 300 <<<"$body")"; return 1; }
}

# --- edge / TLS ---------------------------------------------------------------------------------
# Reaching any https URL above already validated the chain against Caddy's root; this adds the
# plain-HTTP redirect and the two bind mounts that hazard §4.3 warns can silently become empty
# directories. The mounts are now compose-file-relative rather than ${PWD}-based, which is what closed
# that hazard — these two checks stay because they are what proves it stayed closed.
redirects_to_https() {
    local code
    code="$(curl -sS -m 15 -o /dev/null -w '%{http_code}' "http://$APP_DOMAIN/")"
    [[ "$code" == 301 || "$code" == 302 || "$code" == 308 ]] || { echo "http:// returned HTTP $code, not a redirect"; return 1; }
    echo "HTTP $code"
}
check "http://$APP_DOMAIN redirects to https" redirects_to_https
check "Caddyfile mounted as a file (not a directory)" compose exec -T caddy test -f /etc/caddy/Caddyfile
check "realm import mounted as a file" compose exec -T keycloak test -f /opt/keycloak/data/import/pensieve-realm.json

# --- the backend, through the app chain -----------------------------------------------------------
# secureMode=false here IS audit B3 happening: the stack booted green on a working datasource with
# permit-all and enforcement off. Named check, not a timeout, so it cannot be mistaken for slowness.
check "backend reports secureMode=true (audit B3: fail-open profile slip)" \
    expect_json "https://$APP_DOMAIN/api/heartbeat" '.status == "online" and .secureMode == true'

private_ports() {
    local svc cid ports bad=""
    for svc in frontend backend mcp keycloak db keycloak-db; do
        cid="$(compose ps -q "$svc" 2>/dev/null)" || continue
        [[ -n "$cid" ]] || continue
        if [[ "$svc" == "db" ]]; then
            # The app db deliberately publishes 127.0.0.1:5432 for IDE access over an SSH tunnel
            # (compose.production.yaml, 2026-08-17). Loopback-only is the invariant this check
            # protects: any binding on a non-loopback address is a failure.
            ports="$(docker inspect -f '{{json .NetworkSettings.Ports}}' "$cid" \
                | jq -r 'to_entries[] | select(.value != null) | .value[] | select(.HostIp != "127.0.0.1") | .HostIp + ":" + .HostPort' | tr '\n' ' ')"
            [[ -z "${ports// /}" ]] || bad+="db published beyond loopback: $ports; "
            continue
        fi
        ports="$(docker inspect -f '{{json .NetworkSettings.Ports}}' "$cid" \
            | jq -r 'to_entries[] | select(.value != null) | .key' | tr '\n' ' ')"
        [[ -z "${ports// /}" ]] || bad+="$svc publishes $ports; "
    done
    [[ -z "$bad" ]] || { echo "$bad"; return 1; }
    echo "caddy public, db loopback-only, everything else private"
}
check "no public host ports (caddy is the edge; db is loopback-only)" private_ports

flyway_ok() {
    local failed total
    failed="$(compose exec -T db psql -U postgres -d pensieve-db -tAc \
        "select count(*) from flyway_schema_history where success = false" | tr -d '[:space:]')"
    total="$(compose exec -T db psql -U postgres -d pensieve-db -tAc \
        "select count(*) from flyway_schema_history" | tr -d '[:space:]')"
    [[ "$failed" == "0" ]] || { echo "$failed migration(s) recorded as failed"; return 1; }
    [[ "${total:-0}" -gt 0 ]] || { echo "flyway_schema_history is empty — migrations did not run"; return 1; }
    echo "$total migrations applied, 0 failed"
}
check "flyway migrated a clean production database" flyway_ok

# --- the imported realm (hazard §4.2 — this is the one-shot, irreversible part) --------------------
# kcadm from inside the container: the Keycloak image ships no curl, and going in over the compose
# network sidesteps the edge entirely (TLS, headers) — the realm inspection needs none of it.
kc() { compose exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@" --config /tmp/kcadm.config; }
if kc config credentials --server http://localhost:8080 --realm master \
        --user "$KC_ADMIN_USER" --password "$KC_ADMIN_PASSWORD" >/dev/null 2>&1; then
    KC_ADMIN_OK=1
    kc get realms/pensieve      > "$WORK_DIR/realm.json"  2>/dev/null || true
    kc get clients -r pensieve  > "$WORK_DIR/clients.json" 2>/dev/null || true
    kc get client-scopes -r pensieve > "$WORK_DIR/scopes.json" 2>/dev/null || true
else
    KC_ADMIN_OK=0
fi

if [[ "$KC_ADMIN_OK" == "1" ]]; then
    no_placeholders() {
        local hits
        hits="$(cat "$WORK_DIR"/realm.json "$WORK_DIR"/clients.json "$WORK_DIR"/scopes.json 2>/dev/null \
            | grep -o '\${PENSIEVE_[A-Z_]*}' | sort -u | tr '\n' ' ' || true)"
        [[ -z "${hits// /}" ]] || { echo "unresolved: $hits"; return 1; }
        echo "all ten \${PENSIEVE_*} placeholders resolved"
    }
    check "no unresolved \${PENSIEVE_*} placeholder in the imported realm" no_placeholders

    check "pensieve-web redirect URI is https://$APP_DOMAIN/api/auth/callback" \
        jq -e --arg want "https://$APP_DOMAIN/api/auth/callback" \
            '[.[] | select(.clientId=="pensieve-web") | .redirectUris[]] | index($want) != null' "$WORK_DIR/clients.json"

    web_secret_matches() {
        local id secret
        id="$(jq -r '.[] | select(.clientId=="pensieve-web") | .id' "$WORK_DIR/clients.json")"
        [[ -n "$id" ]] || { echo "pensieve-web client not found in the realm"; return 1; }
        secret="$(kc get "clients/$id/client-secret" -r pensieve 2>/dev/null | jq -r '.value // empty' | tr -d '\r')"
        [[ "$secret" == "$OIDC_CLIENT_SECRET" ]] \
            || { echo "client secret does not match OIDC_CLIENT_SECRET (PENSIEVE_WEB_CLIENT_SECRET did not substitute)"; return 1; }
    }
    check "pensieve-web client secret == OIDC_CLIENT_SECRET" web_secret_matches

    check "audience mapper mints aud=https://$MCP_DOMAIN/mcp" \
        jq -e --arg want "https://$MCP_DOMAIN/mcp" \
            '[.[] | select(.name=="pensieve:read") | .protocolMappers[]?
              | select(.protocolMapper=="oidc-audience-mapper")
              | .config["included.custom.audience"]] | index($want) != null' "$WORK_DIR/scopes.json"

    check "realm SMTP host resolved to $SMTP_HOST" \
        jq -e --arg want "$SMTP_HOST" '.smtpServer.host == $want' "$WORK_DIR/realm.json"
else
    skip "imported realm inspection (5 checks)" \
        "kcadm could not authenticate as '$KC_ADMIN_USER'. On a re-import the bootstrap admin only exists on FIRST boot with an empty keycloak-db; if you blanked KC_ADMIN_* after a real deploy, that is expected."
    SKIPPED=$((SKIPPED + 4))
fi

check "issuer is https://$AUTH_DOMAIN/realms/pensieve" \
    expect_json "https://$AUTH_DOMAIN/realms/pensieve/.well-known/openid-configuration" \
        "$(printf '.issuer == "https://%s/realms/pensieve"' "$AUTH_DOMAIN")"

dcr_rejected() {
    local code
    code="$(http_code -X POST -H 'Content-Type: application/json' \
        --data '{"client_name":"rehearsal-probe","redirect_uris":["https://example.com/cb"]}' \
        "https://$AUTH_DOMAIN/realms/pensieve/clients-registrations/openid-connect")"
    [[ "$code" != "200" && "$code" != "201" ]] \
        || { echo "anonymous client registration SUCCEEDED (HTTP $code) — production must refuse it (§4.8)"; return 1; }
    echo "HTTP $code"
}
check "anonymous dynamic client registration is refused" dcr_rejected

# --- no basic-auth gate on the auth host -----------------------------------------------------------
# The gate was REMOVED 2026-08-18 after four rounds of it breaking the console (Bearer collision,
# mid-session popups, vanished navigation menu — PastIssues has all four). The admin surface is
# protected by Keycloak itself: strong password + enforced OTP + master-realm brute-force detection.
# These checks pin the removal: nothing on this host may answer a Basic challenge, and the console
# page must simply load.
check "admin console page loads with no basic-auth gate" \
    expect_code '^(200|302|303)$' "https://$AUTH_DOMAIN/admin/master/console/"
check "master realm metadata is public (no gate)" \
    expect_code '^200$' "https://$AUTH_DOMAIN/realms/master"

# The Admin REST API must NOT be behind the gate: the console calls it with `Authorization: Bearer`,
# a request has exactly one Authorization header, and a basic-auth challenge there is unsatisfiable —
# the console's credential popup loops forever (found live 2026-08-17). Keycloak enforces its own
# bearer token on these routes, so unauthenticated must still be refused — just not by Caddy.
admin_api_ungated() {
    local code challenge
    code="$(http_code "https://$AUTH_DOMAIN/admin/serverinfo")"
    [[ "$code" == "401" || "$code" == "403" ]] \
        || { echo "/admin/serverinfo -> HTTP $code (expected Keycloak to refuse with 401/403)"; return 1; }
    challenge="$("${CURL[@]}" -D - -o /dev/null "https://$AUTH_DOMAIN/admin/serverinfo" \
        | tr -d '\r' | awk 'tolower($1) == "www-authenticate:" { print $2 }')"
    # grep -i, not ${challenge,,}: macOS ships bash 3.2, which lacks case-conversion expansion.
    if grep -qi '^basic' <<<"$challenge"; then
        echo "/admin/serverinfo answers a BASIC challenge — the gate matcher covers the Admin REST API again, which bricks the console (Authorization header collision)"
        return 1
    fi
    echo "HTTP $code with a non-Basic challenge (Keycloak's own auth, not the Caddy gate)"
}
check "Admin REST API is refused by Keycloak, not the Caddy gate (gate there bricks the console)" admin_api_ungated

# The master TOKEN endpoint must not be gated either: the console refreshes its session through it
# with a background fetch, where browsers do not reliably replay basic credentials — a gate there
# means mid-session popups, and canceling one logs the admin out (found live 2026-08-18). The
# compensating control for leaving it open is brute-force detection ON in the master realm.
token_ungated() {
    local challenge
    challenge="$("${CURL[@]}" -D - -o /dev/null -X POST \
        "https://$AUTH_DOMAIN/realms/master/protocol/openid-connect/token" \
        | tr -d '\r' | awk 'tolower($1) == "www-authenticate:" { print $2 }')"
    if grep -qi '^basic' <<<"$challenge"; then
        echo "the master token endpoint answers a BASIC challenge — mid-session console popups are back"
        return 1
    fi
    echo "no Basic challenge (Keycloak handles its own auth)"
}
check "master token endpoint is NOT behind the gate (gate there breaks console sessions)" token_ungated

# The console's own runtime API (/admin/master/console/{config,whoami}) must not be gated either:
# whoami is a Bearer fetch the console builds its ENTIRE navigation menu from — gate it and every
# admin sees only "Manage realms" (found live 2026-08-18, matcher round four). This is why the gate
# matches the console page paths EXACTLY instead of /admin/master/console/*.
console_api_ungated() {
    local p challenge
    for p in config whoami; do
        challenge="$("${CURL[@]}" -D - -o /dev/null "https://$AUTH_DOMAIN/admin/master/console/$p" \
            | tr -d '\r' | awk 'tolower($1) == "www-authenticate:" { print $2 }')"
        if grep -qi '^basic' <<<"$challenge"; then
            echo "/admin/master/console/$p answers a BASIC challenge — the console loses its menu (whoami is a Bearer fetch)"
            return 1
        fi
    done
    echo "config and whoami answer without a Basic challenge"
}
check "console runtime API (config/whoami) is NOT behind the gate (gate it and the menu vanishes)" console_api_ungated

# --- the login flow, driven the way the app actually drives it -------------------------------------
# Deliberately NOT a hand-built authorize URL. The BFF derives redirect_uri from the origin it believes
# it is serving (web repo src/app/api/auth/login/route.ts), and behind a reverse proxy that belief can
# be wrong in a way nothing else catches: the e2e gate reaches the frontend directly, so its origin is
# always right, and Caddy exists only in this topology. Start from GET /api/auth/login and follow it.
urldecode() { local s="${1//+/ }"; printf '%b' "${s//%/\\x}"; }

bff_redirect_uri() {
    local loc ruri want
    loc="$("${CURL[@]}" -D - -o /dev/null "https://$APP_DOMAIN/api/auth/login" \
        | tr -d '\r' | awk 'tolower($1) == "location:" { print $2 }')"
    [[ -n "$loc" ]] || { echo "GET /api/auth/login did not issue a redirect to Keycloak"; return 1; }
    printf '%s' "$loc" > "$WORK_DIR/authorize_url"
    ruri="$(urldecode "$(sed -n 's/.*[?&]redirect_uri=\([^&]*\).*/\1/p' <<<"$loc")")"
    want="https://$APP_DOMAIN/api/auth/callback"
    [[ "$ruri" == "$want" ]] || {
        echo "the BFF asked Keycloak for redirect_uri=$ruri"
        echo "the realm registers only $want, so Keycloak will reject the login with"
        echo "'Invalid parameter: redirect_uri'. The BFF is deriving its origin from the container's"
        echo "own bind address instead of the proxied Host — login is broken in this topology."
        return 1
    }
    echo "$ruri"
}
check "the BFF asks Keycloak for redirect_uri=https://$APP_DOMAIN/api/auth/callback" bff_redirect_uri

login_page_public() {
    local url html
    url="$(cat "$WORK_DIR/authorize_url" 2>/dev/null)"
    [[ -n "$url" ]] || { echo "no authorize URL captured — the previous check did not get one"; return 1; }
    html="$(get_json "$url")" \
        || { echo "Keycloak rejected the BFF's authorization request (no 200 login page)"; return 1; }
    grep -qi 'kc-form\|<form' <<<"$html" || { echo "response is not a login form"; return 1; }
    printf '%s' "$html" > "$WORK_DIR/login.html"
    echo "login form rendered ($(wc -c < "$WORK_DIR/login.html" | tr -d ' ') bytes), and it is not behind the admin gate"
}
check "Keycloak serves the login page for that request (NOT gated)" login_page_public

login_assets_public() {
    local asset
    [[ -s "$WORK_DIR/login.html" ]] || { echo "no login page captured by the previous check"; return 1; }
    asset="$(grep -oE '/resources/[^"'"'"' ]+\.(css|js)' "$WORK_DIR/login.html" | head -1)"
    [[ -n "$asset" ]] || { echo "login page references no /resources/ asset — cannot verify the gate"; return 1; }
    expect_code '^200$' "https://$AUTH_DOMAIN$asset"
}
check "login page /resources/ assets are NOT gated (unstyled-login hazard)" login_assets_public

# --- the MCP sidecar ------------------------------------------------------------------------------
# The sidecar and the backend validate the SAME audience; if either disagrees with the realm's mapper,
# every token is rejected on both sides. The two checks below and the mapper check above are that
# three-way agreement, verified without needing a token.
check "sidecar advertises resource https://$MCP_DOMAIN/mcp" \
    expect_json "https://$MCP_DOMAIN/.well-known/oauth-protected-resource/mcp" \
        "$(printf '.resource == "https://%s/mcp"' "$MCP_DOMAIN")"
check "sidecar advertises authorization server https://$AUTH_DOMAIN/realms/pensieve" \
    expect_json "https://$MCP_DOMAIN/.well-known/oauth-protected-resource/mcp" \
        "$(printf '.authorization_servers[0] == "https://%s/realms/pensieve"' "$AUTH_DOMAIN")"

mcp_enforces() {
    local hdrs
    hdrs="$("${CURL[@]}" -D - -o /dev/null -X POST -H 'Content-Type: application/json' \
        --data '{"jsonrpc":"2.0","method":"tools/list","id":1}' "https://$MCP_DOMAIN/mcp")"
    grep -qiE '^HTTP/[0-9.]+ 401' <<<"$hdrs" \
        || { echo "tokenless POST /mcp was not refused: $(head -1 <<<"$hdrs" | tr -d '\r')"; return 1; }
    grep -qi '^www-authenticate:' <<<"$hdrs" \
        || { echo "401 returned but no WWW-Authenticate challenge (clients cannot discover the auth server)"; return 1; }
    echo "401 + WWW-Authenticate (MCP_AUTH_MODE=required is in force)"
}
check "tokenless POST /mcp is refused with a challenge" mcp_enforces

# --- the whole login, driven end to end -------------------------------------------------------------
# The strongest check here, and the reason it is worth creating a throwaway user for: a completed login
# is the only thing that exercises the TOKEN. It proves the code exchange (which uses the internal
# issuer), the id_token's `iss`, and — because the callback calls GET /v1/auth/me with the access token
# before it can report a role — that the SECURED BACKEND accepted the token's `aud` and `iss`. A role of
# "unknown" means the backend rejected it, which is exactly the mismatch a bad audience mapper causes.
#
# The throwaway passwords below are generated to satisfy the PRODUCTION realm's password policy:
#   length(12) and digits(1) and lowerCase(1) and upperCase(1) and notUsername and notEmail and
#   passwordHistory(3)
# `kc set-password` goes through the Admin API, which enforces the policy, so a password that misses any
# rule fails there and this check goes red for a reason that has nothing to do with the login path it
# exists to prove. If the policy in keycloak/import-prod/pensieve-realm.json changes, change these with it.
if [[ "$KC_ADMIN_OK" == "1" ]]; then
    login_flow() {
        local user pass jar authurl action cb final role email status uid
        user="rehearsal-login-probe"
        pass="Rehearse1$(openssl rand -hex 4)"   # 17 chars; satisfies the prod realm policy (see note below)
        jar="$WORK_DIR/login-jar.txt"; rm -f "$jar"
        # firstName/lastName are NOT optional here: without a complete profile Keycloak interrupts the
        # login with its VERIFY_PROFILE required action and never issues a code.
        kc create users -r pensieve -s "username=$user" -s enabled=true -s "email=$user@example.com" \
            -s emailVerified=true -s firstName=Rehearsal -s lastName=Probe >/dev/null 2>&1 \
            || { echo "could not create the login probe user"; return 1; }
        kc set-password -r pensieve --username "$user" --new-password "$pass" >/dev/null 2>&1 \
            || { echo "could not set the login probe user's password"; return 1; }

        status=0
        while true; do
            authurl="$(curl -sS -m 20 --cacert "$CA_CERT" -b "$jar" -c "$jar" -D - -o /dev/null \
                "https://$APP_DOMAIN/api/auth/login" | tr -d '\r' | awk 'tolower($1) == "location:" { print $2 }')"
            [[ -n "$authurl" ]] || { echo "GET /api/auth/login did not redirect"; status=1; break; }
            curl -sS -m 20 --cacert "$CA_CERT" -b "$jar" -c "$jar" -o "$WORK_DIR/flow-login.html" "$authurl"
            action="$(grep -oE 'action="[^"]*login-actions/authenticate[^"]*"' "$WORK_DIR/flow-login.html" \
                | head -1 | sed 's/action="//; s/"$//; s/&amp;/\&/g')"
            [[ -n "$action" ]] || { echo "no login form action on the Keycloak page"; status=1; break; }
            cb="$(curl -sS -m 20 --cacert "$CA_CERT" -b "$jar" -c "$jar" -D - -o /dev/null -X POST \
                --data-urlencode "username=$user" --data-urlencode "password=$pass" "$action" \
                | tr -d '\r' | awk 'tolower($1) == "location:" { print $2 }')"
            case "$cb" in
                "https://$APP_DOMAIN/api/auth/callback"*) ;;
                *) echo "credentials did not produce a callback redirect: ${cb:-<none>}"; status=1; break ;;
            esac
            final="$(curl -sS -m 20 --cacert "$CA_CERT" -b "$jar" -c "$jar" -D - -o /dev/null "$cb" \
                | tr -d '\r' | awk 'tolower($1) == "location:" { print $2 }')"
            case "$final" in
                *"/login"*) echo "the callback bounced back to /login — the code exchange failed"; status=1; break ;;
                "https://$APP_DOMAIN/"*) ;;
                *) echo "callback redirected somewhere unexpected: ${final:-<none>}"; status=1; break ;;
            esac
            role="$(curl -sS -m 20 --cacert "$CA_CERT" -b "$jar" -c "$jar" \
                "https://$APP_DOMAIN/api/auth/session" | jq -r '.data.role // "missing"')"
            email="$(curl -sS -m 20 --cacert "$CA_CERT" -b "$jar" -c "$jar" \
                "https://$APP_DOMAIN/api/auth/session" | jq -r '.data.email // "missing"')"
            [[ "$email" == "$user@example.com" ]] \
                || { echo "session email is '$email' — the id_token did not land in the session"; status=1; break; }
            [[ "$role" != "unknown" && "$role" != "guest" && "$role" != "missing" ]] \
                || { echo "session role is '$role' — the BACKEND rejected the access token (check aud/iss)"; status=1; break; }
            echo "logged in, landed on $final, session role=$role — the backend accepted the token"
            break
        done

        # Always remove the probe, even on failure; a KEEP_STACK run should be left clean for the human.
        uid="$(kc get users -r pensieve -q "username=$user" 2>/dev/null | jq -r '.[0].id // empty' | tr -d '\r')"
        [[ -n "$uid" ]] && kc delete "users/$uid" -r pensieve >/dev/null 2>&1
        return "$status"
    }
    check "a real login completes and the backend accepts the token" login_flow
else
    skip "a real login completes and the backend accepts the token" \
        "needs kcadm to create a throwaway probe user"
fi

# --- Phase B0: the email half (opt-in — it needs a real relay and a real inbox) ---------------------
if [[ -n "${SMTP_TEST_TO:-}" && "$KC_ADMIN_OK" == "1" ]]; then
    smtp_test() {
        local adminid
        adminid="$(kc get users -r master -q "username=$KC_ADMIN_USER" 2>/dev/null | jq -r '.[0].id // empty' | tr -d '\r')"
        [[ -n "$adminid" ]] || { echo "could not find the bootstrap admin user in the master realm"; return 1; }
        # testSMTPConnection sends to the authenticated admin's own address, so set one first.
        kc update "users/$adminid" -r master -s "email=$SMTP_TEST_TO" >/dev/null 2>&1 \
            || { echo "could not set an email address on the admin user"; return 1; }
        jq -n --arg host "$SMTP_HOST" --arg port "$SMTP_PORT" --arg from "$SMTP_FROM" \
              --arg user "${SMTP_USER:-}" --arg pass "${SMTP_PASSWORD:-}" \
              --arg starttls "${SMTP_STARTTLS:-false}" --arg ssl "${SMTP_SSL:-false}" \
              '{host:$host, port:$port, from:$from, fromDisplayName:"The Game Pensieve",
                replyTo:$from, envelopeFrom:$from, auth:(if $user == "" then "false" else "true" end),
                user:$user, password:$pass, starttls:$starttls, ssl:$ssl}' > "$WORK_DIR/smtp.json"
        compose cp "$WORK_DIR/smtp.json" keycloak:/tmp/smtp.json >/dev/null 2>&1 \
            || { echo "could not copy the smtp body into the keycloak container"; return 1; }
        # kcadm exits non-zero and prints "Failed to send email" when the relay refuses; a successful
        # send is a silent 204. Key off the exit status, not the text.
        local out
        if ! out="$(kc create realms/pensieve/testSMTPConnection -f /tmp/smtp.json 2>&1)"; then
            echo "Keycloak could not send through the relay: $(head -3 <<<"$out" | tr '\n' ' ')"
            return 1
        fi
        echo "Keycloak accepted the relay and sent to $SMTP_TEST_TO — CONFIRM IT ARRIVED (and is not in spam)"
    }
    check "Phase B0: Keycloak sends through the real SMTP relay" smtp_test
elif [[ -n "${SMTP_TEST_TO:-}" ]]; then
    skip "Phase B0: SMTP relay test" "kcadm could not authenticate, so the send could not be attempted"
else
    skip "Phase B0: SMTP relay test" \
        "set SMTP_TEST_TO=<your inbox> (with the REAL relay values in $ENV_FILE) to run it. Until then the SMTP half of the rehearsal has NOT been done — hazard §4.2 makes it one-shot in production."
fi

# --- optional: a user to complete the browser half with --------------------------------------------
if [[ "${CREATE_TEST_USER:-0}" == "1" && "$KC_ADMIN_OK" == "1" ]]; then
    TEST_USER="rehearsal"
    TEST_PASSWORD="Rehearse1$(openssl rand -hex 4)"   # 17 chars; same policy constraints as the login probe
    # firstName/lastName included deliberately: without them Keycloak's VERIFY_PROFILE required action
    # interrupts the first login with a profile form, which is not what you are here to look at.
    if kc create users -r pensieve -s "username=$TEST_USER" -s enabled=true \
            -s "email=$TEST_USER@example.com" -s emailVerified=true \
            -s firstName=Rehearsal -s lastName=User >/dev/null 2>&1 \
       && kc set-password -r pensieve --username "$TEST_USER" --new-password "$TEST_PASSWORD" >/dev/null 2>&1; then
        printf '  MADE  test user %s / %s (pensieve realm)\n' "$TEST_USER" "$TEST_PASSWORD"
    else
        printf '  WARN  could not create the test user — create one in the admin console instead\n'
    fi
fi

# ================================================================================================
log "result"
# ================================================================================================
printf '  %s passed, %s failed, %s skipped\n' "$PASSED" "$FAILED" "$SKIPPED"

cat <<EOF

Not covered by any local rehearsal — these need a Tier 3 staging Droplet or a human:
  - ACME / Let's Encrypt against real DNS, and the linux/amd64 images (this ran host arch)
  - Droplet sizing, the 2 GB swap, and the firewall
  - real inbox deliverability: SPF/DKIM on the ${SMTP_FROM:-<from>} domain — the SMTP check can only
    prove Keycloak handed the message to the relay, never that it reached an inbox unfiltered
  - the app in a real browser. The login flow itself IS checked above (driven end to end, token
    validated by the backend), but nothing here renders a page. To look at it yourself:
      KEEP_STACK=1 CREATE_TEST_USER=1 $0 ${ENV_FILE_ARG}
      sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain $CA_CERT
    then open https://$APP_DOMAIN and log in as the printed user.
EOF

if (( FAILED > 0 )); then
    printf '\nfailed checks:\n%s' "$FAILED_NAMES"
    printf '\n=== [rehearsal] FAILED — %s check(s) did not pass ===\n' "$FAILED"
    exit 1
fi
printf '\n=== [rehearsal] PASSED — the production topology stands up and verifies locally ===\n'
