#!/usr/bin/env bash
#
# Multi-role test data seeder for LIVE environments (dev/staging).
#
# Seeds the data set documented in documentation/Notes.md ("Seeding Multi-Role Test Data"):
# one bootstrap admin, eight users covering TRIAL/PAID/LAPSED, two public showcases, and a
# populated default showcase. It is the live-environment consumer of the seed set; the
# SeededUsersFixture/SeededDataMatrixTests pair is the integration-test consumer. Both run the
# same choreography over the same seed files in src/main/resources/seeders so they never drift.
#
# Usage:
#   ./scripts/seed-test-data.sh
#
# Parameters (environment variables):
#   BASE_URL          API base URL                       (default: http://localhost:8080)
#   ADMIN_EMAIL       bootstrap admin account email      (default: seeder-admin@email.com)
#   ADMIN_PASSWORD    bootstrap admin account password   (default: seeder-admin)
#   KEYCLOAK_URL      Keycloak base URL                  (default: http://localhost:8081)
#   KEYCLOAK_REALM    realm                              (default: pensieve)
#   KEYCLOAK_CLIENT   public client for the password grant (default: pensieve-test-client)
#   KEYCLOAK_ADMIN_USER / KEYCLOAK_ADMIN_PASSWORD  Keycloak admin creds (default: admin / admin)
#   SQL_CMD           command prefix that runs psql for the one bootstrap SQL statement
#                     (default: docker compose exec -T db psql -U postgres -d pensieve-db)
#                     e.g. for a host database: SQL_CMD="psql -h localhost -U postgres -d pensieve-db"
#
# Preconditions:
#   - The API is running with SPRING_PROFILES_ACTIVE including "secured", with its working
#     directory at the repo root (the default-showcase step uses POST /v1/function/seedSampleData,
#     which reads sampleData.json from the server's working directory).
#   - Keycloak is running and reachable at KEYCLOAK_URL with the `pensieve` realm imported. Accounts
#     are created in Keycloak; each account's users row is JIT-provisioned by the API on first login.
#   - No admin exists yet, or the admin is the account this script provisions. The
#     uq_users_single_admin index allows exactly one pinned admin; if a different admin already
#     exists (e.g. the claimed default-showcase row from the documented bootstrap), the UPDATE
#     below fails loudly — that is deliberate. This script targets fresh dev databases.
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
SQL_CMD="${SQL_CMD:-docker compose exec -T db psql -U postgres -d pensieve-db}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEEDERS_DIR="$SCRIPT_DIR/../src/main/resources/seeders"
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

run_sql() {
    # shellcheck disable=SC2086 — SQL_CMD is intentionally word-split (it is a command prefix)
    $SQL_CMD -v ON_ERROR_STOP=1 -q -c "$1" >/dev/null || fail "SQL failed: $1"
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
    code="$(curl -sS -o /dev/null -w '%{http_code}' -X POST \
        "$KEYCLOAK_URL/admin/realms/$KEYCLOAK_REALM/users" \
        -H "Authorization: Bearer $admin" -H 'Content-Type: application/json' \
        -d "{\"username\":\"$email\",\"email\":\"$email\",\"firstName\":\"Seed\",\"lastName\":\"User\",\"emailVerified\":true,\"enabled\":true,\"requiredActions\":[],\"credentials\":[{\"type\":\"password\",\"value\":\"$password\",\"temporary\":false}]}")"
    [[ "$code" == "201" || "$code" == "409" ]] || fail "creating Keycloak user $email (HTTP $code)"
}

login() { # EMAIL PASSWORD -> echoes access token (Keycloak direct-access grant)
    curl -sS -X POST "$KEYCLOAK_URL/realms/$KEYCLOAK_REALM/protocol/openid-connect/token" \
        -d "client_id=$KEYCLOAK_CLIENT" -d grant_type=password \
        -d "username=$1" -d "password=$2" -d scope=openid \
        | jq -re '.access_token' || fail "no access token from Keycloak for $1"
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

log "Checking the API at $BASE_URL"
request GET /v1/heartbeat ""
expect_status 200 "API heartbeat"

# ============================ Step 1 — bootstrap the admin ============================

log "Bootstrapping admin $ADMIN_EMAIL"
register "$ADMIN_EMAIL" "$ADMIN_PASSWORD"
ADMIN_TOKEN="$(login "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
# First login JIT-provisions the admin's users row so the pin below has a row to update.
provision "$ADMIN_TOKEN"
# The one statement the API cannot perform: the first admin pin. Idempotent on re-run against the
# same email; fails hard on uq_users_single_admin if a different admin exists (see preconditions).
run_sql "UPDATE users SET role_override='ADMIN' WHERE email='$ADMIN_EMAIL';"

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
request POST /v1/function/seedSampleData "$ADMIN_TOKEN" -H "X-Act-As-Owner: $DEFAULT_ID"
expect_status 200 "seeding the default showcase (server reads sampleData.json from its working directory)"
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
