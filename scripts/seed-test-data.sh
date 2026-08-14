#!/usr/bin/env bash
#
# Multi-role test data seeder for LIVE environments (dev/staging).
#
# Seeds the data set documented in documentation/DevDocumentation.md ("Seeding Multi-Role Test Data"):
# one bootstrap admin, eight users covering TRIAL/PAID/LAPSED, two public showcases, and a
# populated default showcase. It is the live-environment consumer of the seed set; the
# SeededUsersFixture/SeededDataMatrixTests pair is the integration-test consumer. Both run the
# same choreography over the same seed files in src/main/resources/seeders so they never drift.
#
# Usage (against the local SECURED compose stack — the unsecured stack cannot be seeded, see below):
#   docker compose -f dockerCompose/compose.secured.yaml up -d
#   ./scripts/seed-test-data.sh
#
# Parameters (environment variables):
#   BASE_URL          API base URL                       (default: http://localhost:8080)
#   ADMIN_EMAIL       bootstrap admin account email      (default: seeder-admin@email.com)
#   ADMIN_PASSWORD    bootstrap admin account password   (default: seeder-admin)
#   KEYCLOAK_URL      Keycloak base URL                  (default: http://localhost:8081)
#   KEYCLOAK_REALM    realm                              (default: pensieve)
#   KEYCLOAK_CLIENT   client used for the password grant (default: pensieve-test-client)
#   KEYCLOAK_ADMIN_USER / KEYCLOAK_ADMIN_PASSWORD  Keycloak admin creds (default: admin / admin)
#   SQL_CMD           command prefix that runs psql for the one bootstrap SQL statement
#                     (default: docker compose -f dockerCompose/compose.secured.yaml exec -T db psql -U postgres -d pensieve-db)
#                     e.g. for a host database: SQL_CMD="psql -h localhost -U postgres -d pensieve-db"
#
# Preconditions (each is checked in Step 0, which fails with a specific message rather than letting a
# misconfiguration surface later as a confusing 401/403):
#   - The API is running with SPRING_PROFILES_ACTIVE including "secured" (GET /v1/heartbeat must report
#     secureMode=true). Every payload this script sends is read from THIS repo and posted in a request
#     body, so the server's own working directory does not matter.
#     The permit-all build cannot be seeded at all: it resolves every request to the default-showcase
#     owner as GUEST, so no users row is ever provisioned and the admin API answers 403.
#   - Keycloak is running and reachable at KEYCLOAK_URL with the realm imported. Accounts are created in
#     Keycloak; each account's users row is JIT-provisioned by the API on first login.
#   - KEYCLOAK_CLIENT exists in that realm, is enabled, and has direct access grants (the password grant)
#     turned on — this script has no browser, so it can only obtain tokens that way. The dev realm ships
#     `pensieve-test-client` for exactly this. THE PRODUCTION REALM DELIBERATELY DOES NOT: it has only the
#     confidential `pensieve-web` client with direct access grants off, so a deployment importing
#     keycloak/import-prod/pensieve-realm.json cannot be seeded by this script until an operator adds a
#     direct-grant client to that realm by hand. Do not add one to the prod realm import to work around
#     this — a password-grant client is a permanent weakening of a production authorization server, and
#     these fixtures have no business existing in production anyway.
#   - No admin exists yet, or the admin is the account this script provisions. The
#     uq_users_single_admin index allows exactly one pinned admin; if a different admin already
#     exists (e.g. the claimed default-showcase row from the documented bootstrap), the UPDATE
#     below violates that index and fails loudly — that is deliberate. Separately, an UPDATE that
#     matches NO row is also an error (it means SQL_CMD and BASE_URL are pointed at different
#     databases). This script targets fresh dev databases.
#   - Never point this at the integration-test database; the test suite seeds itself.
#
# Idempotency: rerunnable. Tolerated on re-runs: Keycloak user creation -> 409 "already exists", and
# imports reporting rows as existing rather than created. Everything else exits non-zero.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_EMAIL="${ADMIN_EMAIL:-seeder-admin@email.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-seeder-admin}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8081}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-pensieve}"
KEYCLOAK_CLIENT="${KEYCLOAK_CLIENT:-pensieve-test-client}"
KEYCLOAK_ADMIN_USER="${KEYCLOAK_ADMIN_USER:-admin}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SEEDERS_DIR="$SCRIPT_DIR/../src/main/resources/seeders"
# The compose file is named by absolute path so the default works from any working directory. The stack it
# targets is unambiguous either way: the compose file pins `name: the-game-pensieve-api`, so the project no
# longer depends on where the file sits or where this script is run from.
SQL_CMD="${SQL_CMD:-docker compose -f $REPO_ROOT/dockerCompose/compose.secured.yaml exec -T db psql -U postgres -d pensieve-db}"
DEFAULT_SHOWCASE_EMAIL="showcase@internal.local"
EMPTY_IMPORT_BODY='{"data":{"customFields":[],"toys":[],"systems":[],"videoGameBoxes":[],"boardGameBoxes":[],"metadata":[]}}'
EMPTY_FILTERS='{"filters":[]}'
NAME_FILTER='{"filters":[{"key":"system","field":"name","operator":"equals","operand":"anything"}]}'

RESPONSE_FILE="$(mktemp)"
IMPORT_BODY_FILE="$(mktemp)"
trap 'rm -f "$RESPONSE_FILE" "$IMPORT_BODY_FILE"' EXIT

log()  { printf '>>> %s\n' "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

command -v curl >/dev/null || fail "curl is required"
command -v jq >/dev/null || fail "jq is required"

# request METHOD PATH TOKEN [extra curl args...] -> sets STATUS and BODY
request() {
    local method="$1" path="$2" token="$3"
    shift 3
    local args=(-sS -o "$RESPONSE_FILE" -w '%{http_code}' -X "$method" "$BASE_URL$path" -H 'Content-Type: application/json')
    if [[ -n "$token" ]]; then
        args+=(-H "Authorization: Bearer $token")
    fi
    STATUS="$(curl "${args[@]}" "$@")"
    BODY="$(cat "$RESPONSE_FILE")"
}

expect_status() { # expected description
    [[ "$STATUS" == "$1" ]] || fail "$2 (expected HTTP $1, got $STATUS): $BODY"
}

sql_scalar() { # SQL -> echoes the single value the query returns (empty when it returns no row)
    # shellcheck disable=SC2086 — SQL_CMD is intentionally word-split (it is a command prefix)
    $SQL_CMD -v ON_ERROR_STOP=1 -tAq -c "$1" | tr -d '[:space:]' || fail "SQL failed: $1"
}

kc_admin_token() { # -> echoes a Keycloak admin access token
    curl -sS -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
        -d client_id=admin-cli -d grant_type=password \
        -d "username=$KEYCLOAK_ADMIN_USER" -d "password=$KEYCLOAK_ADMIN_PASSWORD" \
        | jq -re '.access_token' || fail "could not get a Keycloak admin token from $KEYCLOAK_URL"
}

# register EMAIL PASSWORD — create the Keycloak account (idempotent: tolerate an already-existing user).
# Passwords live in Keycloak now; the API JIT-provisions each account's users row on first login.
register() {
    local email="$1" password="$2" admin existing code
    admin="$(kc_admin_token)"
    existing="$(curl -sS -G -H "Authorization: Bearer $admin" \
        "$KEYCLOAK_URL/admin/realms/$KEYCLOAK_REALM/users" \
        --data-urlencode "exact=true" --data-urlencode "email=$email" | jq -r 'length')"
    if [[ "$existing" != "0" ]]; then return 0; fi
    # requiredActions=[] + a complete profile keep the account "fully set up" so the password grant works.
    #
    # emailVerified=true is set deliberately and is NOT the realm's (now removed) verifyEmail gate: neither realm
    # forces address confirmation any more, so Keycloak never sets this flag on an admin-created account and
    # every token would carry email_verified=false. The API keys its claim-by-email link on that claim
    # (OwnerResolver), which is what lets a re-run recover when a users row already exists but its keycloak_sub
    # no longer matches — e.g. seeding again after the Keycloak volume was wiped but the database was not.
    # Without it that case dies on the JIT insert's UNIQUE(email) with a 403 instead of relinking.
    code="$(curl -sS -o /dev/null -w '%{http_code}' -X POST \
        "$KEYCLOAK_URL/admin/realms/$KEYCLOAK_REALM/users" \
        -H "Authorization: Bearer $admin" -H 'Content-Type: application/json' \
        -d "{\"username\":\"$email\",\"email\":\"$email\",\"firstName\":\"Seed\",\"lastName\":\"User\",\"emailVerified\":true,\"enabled\":true,\"requiredActions\":[],\"credentials\":[{\"type\":\"password\",\"value\":\"$password\",\"temporary\":false}]}")"
    [[ "$code" == "201" || "$code" == "409" ]] || fail "creating Keycloak user $email (HTTP $code)"
}

login() { # EMAIL PASSWORD -> echoes access token (Keycloak direct-access grant)
    local response token
    response="$(curl -sS -X POST "$KEYCLOAK_URL/realms/$KEYCLOAK_REALM/protocol/openid-connect/token" \
        -d "client_id=$KEYCLOAK_CLIENT" -d grant_type=password \
        -d "username=$1" -d "password=$2" -d scope=openid)"
    # Report Keycloak's own error rather than a bare "no token": the useful cases are an account that is not
    # fully set up (a pending required action) and a client without direct access grants, and both say so here.
    token="$(printf '%s' "$response" | jq -r '.access_token // empty')"
    [[ -n "$token" ]] || fail "no access token from Keycloak for $1 (client '$KEYCLOAK_CLIENT'): $response"
    printf '%s' "$token"
}

provision() { # TOKEN — first authenticated call JIT-provisions the caller's users row (30-day trial)
    request GET /v1/auth/me "$1"
    expect_status 200 "provisioning the users row via GET /v1/auth/me"
}

user_id() { # EMAIL -> echoes id (needs ADMIN_TOKEN; register's id is unavailable on 400 re-runs)
    request GET /v1/admin/users "$ADMIN_TOKEN"
    expect_status 200 "listing users to resolve the id of $1"
    jq -re --arg email "$1" '.data[] | select(.email == $email) | .id' "$RESPONSE_FILE" \
        || fail "user $1 not found in GET /v1/admin/users"
}

pin_role() { # USER_ID ROLE_JSON ("\"PAID\"" or null)
    request POST "/v1/admin/users/$1/role" "$ADMIN_TOKEN" -d "{\"roleOverride\":$2}"
    expect_status 200 "pinning role $2 on user id $1"
}

# seed_user EMAIL PASSWORD FINAL_ROLE SEED_FILE [SLUG NAME]
seed_user() {
    local email="$1" password="$2" final_role="$3" seed_file="$4" slug="${5:-}" name="${6:-}"
    log "Seeding $email (final role $final_role, data $seed_file)"
    register "$email" "$password"
    local token
    token="$(login "$email" "$password")"
    # First authenticated call JIT-provisions the users row (30-day trial) so the admin can pin its role.
    provision "$token"
    local id
    id="$(user_id "$email")"
    # A JIT-provisioned account derives to TRIAL, which lacks IMPORT — pin PAID so the account can load its data.
    pin_role "$id" '"PAID"'
    # The import endpoint takes the bare seed file wrapped under a "data" key. Imports are
    # idempotent (existing rows resolve by name/title), so re-runs are safe no-ops. The wrapped
    # body goes through a file — piping into request() would run it in a subshell and lose STATUS.
    jq '{data: .}' "$SEEDERS_DIR/$seed_file" > "$IMPORT_BODY_FILE"
    request POST /v1/function/import "$token" --data-binary "@$IMPORT_BODY_FILE"
    expect_status 200 "importing $seed_file as $email"
    jq -e '.errors == null or (.errors | length == 0)' "$RESPONSE_FILE" >/dev/null \
        || fail "import of $seed_file as $email reported errors: $BODY"
    # Always pin the final role — never clear to a derived role, which would silently lapse later.
    pin_role "$id" "\"$final_role\""
    if [[ -n "$slug" ]]; then
        request POST "/v1/admin/users/$id/showcase" "$ADMIN_TOKEN" -d "{\"slug\":\"$slug\",\"name\":\"$name\"}"
        expect_status 200 "granting showcase '$slug' to $email"
    fi
}

# ============================ Step 0 — readiness ============================
#
# Every check here is a precondition that would otherwise fail much later, and much less legibly: an
# unsecured API answers 403 from the admin API three steps in, and a client without the password grant
# fails on the first login with nothing pointing at the client as the cause.

log "Checking the API at $BASE_URL"
request GET /v1/heartbeat ""
expect_status 200 "API heartbeat"
# secureMode mirrors the `secured` profile. Without it the API permits everything, resolves every request
# to the default-showcase owner as GUEST, and JIT-provisions nothing — there is no seeding to be done.
jq -e '.data.secureMode == true' "$RESPONSE_FILE" >/dev/null || fail \
    "the API at $BASE_URL is running UNSECURED (heartbeat secureMode is not true); seeding needs the
  \`secured\` profile. With the local stack:  docker compose -f dockerCompose/compose.secured.yaml up -d"

log "Checking Keycloak at $KEYCLOAK_URL (realm $KEYCLOAK_REALM)"
curl -sSf -o /dev/null "$KEYCLOAK_URL/realms/$KEYCLOAK_REALM/.well-known/openid-configuration" 2>/dev/null \
    || fail "no realm '$KEYCLOAK_REALM' at $KEYCLOAK_URL — is Keycloak up, and did it import the realm?
  --import-realm only runs on a first boot into an empty volume, so a keycloak_data volume left over from
  an older realm keeps serving that older realm (and can predate the '$KEYCLOAK_CLIENT' client). To force
  a re-import without touching the app database:
    docker compose -f dockerCompose/compose.secured.yaml stop keycloak
    docker compose -f dockerCompose/compose.secured.yaml rm -f keycloak
    docker volume rm the-game-pensieve-api_keycloak_data
    docker compose -f dockerCompose/compose.secured.yaml up -d keycloak"

# Prove the admin credentials work before anything depends on them. kc_admin_token's own `fail` runs
# inside a command substitution, so it can only exit that subshell — every later caller would carry on
# with an empty bearer and report some downstream 401 instead. Checking here makes it one clear error.
KC_ADMIN_TOKEN="$(kc_admin_token)" || true
[[ -n "$KC_ADMIN_TOKEN" ]] || fail "could not get a Keycloak admin token from $KEYCLOAK_URL as
  '$KEYCLOAK_ADMIN_USER' — check KEYCLOAK_ADMIN_USER / KEYCLOAK_ADMIN_PASSWORD (the script needs admin
  rights on the master realm to create the seed accounts)."

# The script has no browser, so the password grant is the only way it can obtain a token. Checking the
# client up front turns "no access token for trial1@email.com" into a message naming the actual cause —
# most usefully when pointed at a deployment running the prod realm, which ships no direct-grant client.
KC_CLIENT_JSON="$(curl -sS -G -H "Authorization: Bearer $KC_ADMIN_TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$KEYCLOAK_REALM/clients" --data-urlencode "clientId=$KEYCLOAK_CLIENT")"
jq -e '.[0].enabled == true and .[0].directAccessGrantsEnabled == true' <<<"$KC_CLIENT_JSON" >/dev/null || fail \
    "the Keycloak client '$KEYCLOAK_CLIENT' in realm '$KEYCLOAK_REALM' is missing, disabled, or has direct
  access grants turned off, so this script cannot obtain tokens. The dev realm ships 'pensieve-test-client'
  for this; the production realm deliberately ships no direct-grant client and is not a seeding target.
  Set KEYCLOAK_CLIENT to a direct-grant client in that realm if one exists. Keycloak returned: $KC_CLIENT_JSON"

# ============================ Step 1 — bootstrap the admin ============================

log "Bootstrapping admin $ADMIN_EMAIL"
register "$ADMIN_EMAIL" "$ADMIN_PASSWORD"
ADMIN_TOKEN="$(login "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
# First login JIT-provisions the admin's users row so the pin below has a row to update.
provision "$ADMIN_TOKEN"
# The one statement the API cannot perform: the first admin pin. Idempotent on re-run against the same
# email; fails hard on uq_users_single_admin if a different admin exists (see preconditions). RETURNING
# turns the other failure — an UPDATE that matches nothing — into an error too: a silent zero-row update
# would leave every later admin call answering 403 with nothing to point at the cause. The row is missing
# when SQL_CMD reaches a different database than BASE_URL does, which is the whole reason to check.
PINNED_ADMIN_ID="$(sql_scalar "UPDATE users SET role_override='ADMIN' WHERE email='$ADMIN_EMAIL' RETURNING id;")"
[[ -n "$PINNED_ADMIN_ID" ]] || fail "no users row with email '$ADMIN_EMAIL' to pin as ADMIN. The account was
  just provisioned through $BASE_URL, so the database reached by SQL_CMD is not the one the API is using:
  SQL_CMD=$SQL_CMD"

# ============================ Step 2+3 — users, data, showcase grants ============================

seed_user "trial1@email.com"    "trial1"    "TRIAL"  "seedTrialData1.json"
seed_user "trial2@email.com"    "trial2"    "TRIAL"  "seedTrialData2.json"
seed_user "paid1@email.com"     "paid1"     "PAID"   "seedPaidData1.json"
seed_user "paid2@email.com"     "paid2"     "PAID"   "seedPaidData2.json"
seed_user "lapsed1@email.com"   "lapsed1"   "LAPSED" "seedLapsedData1.json"
seed_user "lapsed2@email.com"   "lapsed2"   "LAPSED" "seedLapsedData2.json"
seed_user "showcase1@email.com" "showcase1" "PAID"   "seedShowcaseData1.json" "showcase-one" "Showcase One"
seed_user "showcase2@email.com" "showcase2" "PAID"   "seedShowcaseData2.json" "showcase-two" "Showcase Two"

# ============================ Step 4 — populate the default showcase ============================

log "Populating the default showcase ($DEFAULT_SHOWCASE_EMAIL) via admin impersonation"
DEFAULT_ID="$(user_id "$DEFAULT_SHOWCASE_EMAIL")"
# Impersonation adopts the target's role, and the unpinned marker row derives to LAPSED (no
# IMPORT) — pin it PAID for the import, then clear (anonymous resolution ignores its role).
pin_role "$DEFAULT_ID" '"PAID"'
# Posts the payload through /v1/function/import rather than calling /v1/function/seedSampleData.
# The seed endpoints are gated on SEED, which only ADMIN holds, and impersonation adopts the
# TARGET's role — so the seed endpoint is unreachable here by construction, and the target cannot
# be pinned ADMIN either (uq_users_single_admin allows exactly one, and it is this script's own
# admin). Importing is what the PAID pin above authorizes, and it is the same call every other
# seeded user makes. It also drops this step's dependency on the server's working directory.
jq '{data: .}' "$REPO_ROOT/sampleData.json" > "$IMPORT_BODY_FILE"
request POST /v1/function/import "$ADMIN_TOKEN" -H "X-Act-As-Owner: $DEFAULT_ID" --data-binary "@$IMPORT_BODY_FILE"
expect_status 200 "seeding the default showcase"
pin_role "$DEFAULT_ID" null

# ============================ Step 5 — smoke assertions ============================

log "Verifying the role/showcase matrix"

# GUEST: the default showcase is readable, populated, and filterable anonymously.
request POST /v1/systems/function/search "" -d "$EMPTY_FILTERS"
expect_status 200 "anonymous read of the default showcase"
jq -e '.data | length > 0' "$RESPONSE_FILE" >/dev/null || fail "the default showcase is empty"
request POST /v1/systems/function/search "" -d "$NAME_FILTER"
expect_status 200 "anonymous filtered search of the default showcase"

# TRIAL: import is denied.
TRIAL_TOKEN="$(login trial1@email.com trial1)"
request POST /v1/function/import "$TRIAL_TOKEN" -d "$EMPTY_IMPORT_BODY"
expect_status 403 "TRIAL import must be forbidden"

# PAID: filtered search succeeds.
PAID_TOKEN="$(login paid1@email.com paid1)"
request POST /v1/systems/function/search "$PAID_TOKEN" -d "$NAME_FILTER"
expect_status 200 "PAID filtered search"

# LAPSED: unfiltered list ok, filter 402, write 403.
LAPSED_TOKEN="$(login lapsed1@email.com lapsed1)"
request POST /v1/systems/function/search "$LAPSED_TOKEN" -d "$EMPTY_FILTERS"
expect_status 200 "LAPSED unfiltered list"
request POST /v1/systems/function/search "$LAPSED_TOKEN" -d "$NAME_FILTER"
expect_status 402 "LAPSED filtered search must be payment-required"
request POST /v1/systems "$LAPSED_TOKEN" -d '{"system":{"name":"lapsed-write-probe","generation":1,"handheld":false,"customFieldValues":[]}}'
expect_status 403 "LAPSED write must be forbidden"

# ADMIN: the admin API answers, the single-admin rule holds, non-admins are rejected.
request GET /v1/admin/users "$ADMIN_TOKEN"
expect_status 200 "admin user listing"
PAID2_ID="$(user_id paid2@email.com)"
request POST "/v1/admin/users/$PAID2_ID/role" "$ADMIN_TOKEN" -d '{"roleOverride":"ADMIN"}'
expect_status 400 "pinning a second admin must be rejected"
request GET /v1/admin/users "$PAID_TOKEN"
expect_status 403 "a non-admin must not reach the admin API"

# Showcases: both granted showcases are listed and switchable; views are GUEST-scoped.
request GET /v1/showcases ""
expect_status 200 "public showcase directory"
jq -e '[.data[].slug] | contains(["showcase-one","showcase-two"])' "$RESPONSE_FILE" >/dev/null \
    || fail "the showcase directory is missing the seeded showcases: $BODY"
request POST /v1/systems/function/search "" -d "$EMPTY_FILTERS" -H "X-Showcase: showcase-one"
expect_status 200 "anonymous view of showcase-one"
ONE_NAMES="$(jq -c '[.data[].name] | sort' "$RESPONSE_FILE")"
request POST /v1/systems/function/search "" -d "$EMPTY_FILTERS" -H "X-Showcase: showcase-two"
expect_status 200 "anonymous view of showcase-two"
TWO_NAMES="$(jq -c '[.data[].name] | sort' "$RESPONSE_FILE")"
[[ "$ONE_NAMES" != "$TWO_NAMES" ]] || fail "showcase-one and showcase-two served identical data"
request POST /v1/systems/function/search "" -d "$EMPTY_FILTERS" -H "X-Showcase: no-such-slug"
expect_status 404 "an unknown showcase slug must be a 404"
request POST /v1/systems "$PAID_TOKEN" -H "X-Showcase: showcase-one" \
    -d '{"system":{"name":"viewer-write-probe","generation":1,"handheld":false,"customFieldValues":[]}}'
expect_status 403 "a write while viewing a showcase must be forbidden (GUEST-scoped)"

log "Done. Seeded and verified: admin ($ADMIN_EMAIL), trial1/trial2, paid1/paid2, lapsed1/lapsed2, showcase1 (showcase-one), showcase2 (showcase-two), and the default showcase."
