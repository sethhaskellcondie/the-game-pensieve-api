# Keycloak — MCP Authorization Server (self-hosted)

> ## ⚠️ One-way door: `import-prod/pensieve-realm.json` is imported exactly once
>
> `--import-realm` runs on the **first boot against an empty `keycloak-db` volume, and never again.**
> Every value in the production realm file — the password policy, refresh-token rotation, the SMTP block,
> the client secret, the audience mapper, the scope layout — is frozen at that moment. Editing the JSON or
> `.env` afterwards changes nothing.
>
> Correcting a mistake means one of two things, and neither is cheap:
>
> - **Edit the live realm by hand** in the admin console. The realm is a database, so most settings really
>   are editable — but `pensieve-realm.json` silently stops being the source of truth from then on, and the
>   drift is invisible until someone rebuilds from the file.
> - **Destroy `keycloak_db_data` and re-import.** That deletes every user account. `users.keycloak_sub` in
>   the app database is a unique reference into that volume, so every existing user is orphaned from their
>   collection and silently JIT-provisions an empty new row. **Never run `down -v` on the Droplet.**
>
> Get this file right before the first `docker compose up`. The dev realm in `import/` carries no such
> weight — wipe and re-import it freely.

Self-hosted Keycloak (**26.7**) that acts as the OAuth 2.1 Authorization Server for the MCP rollout
(Phase 3 of `../localFiles/mcp_rollout.md`). Runs as the `keycloak` service in both development compose
files, `../dockerCompose/compose.unsecured.yaml` and `../dockerCompose/compose.secured.yaml` (the second
includes the first and only switches the backend to the `secured` profile — the Keycloak service is
identical in each). Those files bind-mount `import/` from here as `../keycloak/import`, a path relative to
the compose file rather than to your shell, so it resolves the same wherever compose is run from.

## What's here

- `import/pensieve-realm.json` — the **fully declarative** realm, imported on first boot
  (`--import-realm`). It is self-contained (no post-import script): the **`pensieve`** realm, a public
  dev client **`pensieve-test-client`** (authcode + PKCE/S256 + direct-access grants, localhost
  redirect URIs — for the MCP Inspector and the test suite), a **confidential** client **`pensieve-web`**
  (authcode + PKCE/S256, direct-access OFF — the Next.js BFF; secret `dev-web-secret-change-me`, dev
  only), two test users (`seth`, `otheruser`; password `password`), and — folded in so the compose
  Keycloak and the test Keycloak container get identical config:
  1. a **`pensieve:read`** client scope with an **Audience mapper** (`aud` = the `/mcp` URL) — needed
     because **Keycloak does not honor the RFC 8707 `resource` parameter** — assigned as a default
     scope of the client (and a realm default), so tokens carry `aud` automatically;
  2. the full set of built-in client scopes (`basic`/`email`/`profile`/…) so tokens carry
     `sub` + `email` (KC 26 sources `sub` from the `basic` scope);
  3. **anonymous DCR for localhost** via the Trusted Hosts policy (registered clients' redirect URIs
     must be localhost; the sender-IP check is disabled so requests through the Docker bridge work);
  4. **self-service password reset** (`resetPasswordAllowed`; `verifyEmail` is deliberately **off**),
     with `smtpServer` pointed at the `mailpit` dev container — see [Account emails](#account-emails).
- `import-prod/pensieve-realm.json` — the **production** realm, mounted by
  `../dockerCompose/compose.production.yaml`. Derived from the dev realm with the dev-only surface removed:
  **no test users**, **no `pensieve-test-client`** (no public client, no direct-access grants), **no
  anonymous DCR** (the Trusted Hosts policy is absent, so Keycloak's default policies deny anonymous
  registration — pre-register remote MCP hosts via the admin console instead), and
  `sslRequired=external` (TLS terminates at Caddy). Deployment-specific values are `${...}`
  placeholders (`PENSIEVE_APP_DOMAIN`, `PENSIEVE_MCP_DOMAIN`, `PENSIEVE_WEB_CLIENT_SECRET`,
  `PENSIEVE_SMTP_*`) that
  Keycloak resolves from the service environment at import time — after the first boot, decode a
  token and verify `aud`; a literal `${PENSIEVE_...}` means substitution failed. If you change the
  dev realm, re-apply the equivalent change to the prod file.

  **Prod-only hardening (deliberately NOT mirrored into the dev realm):**
  - `bruteForceProtected` (10 failures, temporary lockout, no permanent lockout) — mirroring it would
    make any test that exercises a failed login flaky once the counter trips.
  - `passwordPolicy: length(12) and digits(1) and lowerCase(1) and upperCase(1) and notUsername and
    notEmail and passwordHistory(3)` — this is the **only interactive credential** on a public app, and
    `registrationAllowed: false` means self-service reset is the only recovery path, so it is worth more
    than Keycloak's defaults. The Admin API enforces the policy on admin-create, and
    `KeycloakTestSupport.ensureUser` creates its users with the password `password`, so mirroring this
    breaks the test suite outright — that is why it stays prod-only.
    Notes on the individual rules: Keycloak has no generic "any letter" rule, so `lowerCase(1)` +
    `upperCase(1)` is the closest thing to "mixed case"; `notUsername`/`notEmail` reject a password *equal
    to* the username or email address (they are not substring checks); `passwordHistory(3)` blocks reusing
    the last three passwords, which matters because the reset flow is the recovery path and a user who
    resets back to the compromised password has recovered nothing.
    **`scripts/prod-rehearsal.sh` generates throwaway passwords against this policy** (`Rehearse1<hex>`,
    17 chars) in two places. Change the policy and those must change with it, or the strongest check in
    the script — a real login, end to end — fails at `kc set-password` for a reason that has nothing to do
    with the login path.
  - **Refresh-token rotation** — `revokeRefreshToken: true`, `refreshTokenMaxReuse: 0`. Keycloak issues a
    new refresh token on every refresh either way; what this adds is *invalidating the old one*. Without
    it a leaked refresh token stays usable for the full session lifespan (and, with `offline_access`, for
    the full 30-day offline lifespan) no matter how many times the real client has refreshed since.
    ⚠️ **`maxReuse: 0` makes a second presentation of a spent token a session-revoking event, not a
    no-op.** The web BFF is built for that: `the-game-pensieve-web-v2/src/proxy.ts` single-flights the
    refresh and keeps a 60-second replay window, so concurrent `/api/*` calls and requests that were
    already in flight when the cookie rotated all share one token exchange. Removing that would log users
    out roughly every 15 minutes. A pre-registered MCP connector must persist the rotated token too.
  - `eventsEnabled` / `adminEventsEnabled` (30-day retention) — the audit trail. Admin events are the
    more valuable half: they record who changed what in the console, which is the only trace of a
    post-deploy change that this import file no longer describes.
  - Offline sessions: **10-day idle, 30-day absolute**. Keycloak's default leaves offline tokens
    unbounded (`offlineSessionMaxLifespanEnabled` defaults to `false`); this pins a hard monthly
    re-auth and reaps abandoned connectors well before that.
    These govern MCP connector tokens — see [Offline tokens](#offline-tokens-for-mcp-connectors).
  - **`pensieve:read` is NOT a realm default client scope.** See
    [Audience segmentation](#audience-segmentation-prod-only) below — this is the one structural
    difference between the two realms, and the one most likely to surprise you.
  - **The post-logout redirect is pinned**, not a wildcard: `post.logout.redirect.uris` on `pensieve-web`
    lists `https://${PENSIEVE_APP_DOMAIN}` and its trailing-slash form, matching how the login
    `redirectUris` were already pinned. The BFF sends its own origin and nothing else
    (`src/app/api/auth/logout/route.ts` → `postLogoutRedirectUri: appOrigin(request)`), so the previous
    `https://…/*` accepted a whole family of URLs nothing ever asks for. Add a value here if a future
    logout ever needs to land on a path.

## Bring it up

```bash
# from the repo root — imports the realm on first boot. Either development compose file works;
# they define the same keycloak service.
docker compose -f dockerCompose/compose.unsecured.yaml up -d keycloak
```

Admin console: <http://localhost:8081> (admin / admin — dev only). Realm discovery:
<http://localhost:8081/realms/pensieve/.well-known/openid-configuration>.

The realm config lives entirely in the import file, so a clean `docker compose -f
dockerCompose/compose.unsecured.yaml down -v` + `up` rebuilds it exactly (no manual step). Edit `import/pensieve-realm.json` to change it.
**`--import-realm` only imports into an empty volume**, so editing the file does nothing to a Keycloak
that has already booted once — a stale `keycloak_data` volume is the usual reason a realm change, or a
client the realm is supposed to ship, appears to be missing.

## Audience segmentation (prod only)

**The `/mcp` audience is attached by a mapper on the `pensieve:read` client scope** — Keycloak does not
honor the RFC 8707 `resource` parameter, so a scope mapper is the only way tokens carry `aud` at all. Both
the sidecar and the backend validate that audience, which makes "who holds `pensieve:read`" the same
question as "whose tokens are accepted."

In the **dev** realm `pensieve:read` sits in `defaultDefaultClientScopes`, so every client in the realm
gets it. In the **prod** realm it does not; only clients that list it explicitly do.

Why they differ, in both directions:

- **Prod removes it** because a realm default is assigned to every client Keycloak creates, including the
  built-ins it makes at realm creation — `admin-cli`, `account`, `security-admin-console`, and the rest.
  `admin-cli` has direct-access grants on, so with `pensieve:read` as a realm default any user's
  username+password could mint a token carrying `aud=https://<MCP>/mcp` and be accepted by both the
  sidecar and the API, bypassing the BFF entirely. Segmentation was nil. `pensieve-web` lists the scope in
  its own `defaultClientScopes`, so **the web app is unaffected** — and it must stay that way, because the
  backend rejects any token without that audience.
- **Dev keeps it** because dev enables anonymous DCR for localhost (the Trusted Hosts policy, for the MCP
  Inspector). A dynamically registered client is created with the realm defaults and nothing else, so
  taking `pensieve:read` off the dev realm default would leave every Inspector-registered client with no
  audience and no way to reach `/mcp`. Production ships no DCR, so it has no equivalent need.

**Consequence for production:** a hand-registered MCP connector gets **no** `pensieve:read` automatically.
Attach it to the client explicitly, or its tokens carry no audience and every `/mcp` call fails. See
[Offline tokens](#offline-tokens-for-mcp-connectors).

**The sidecar now checks the scope, not just the audience.** `the-game-pensieve-mcp/src/httpApp.ts`
verifies the bearer and then requires every scope in `MCP_OAUTH_REQUIRED_SCOPES` (default: whatever
`MCP_OAUTH_SCOPES` advertises, i.e. `pensieve:read`). A verified-but-under-scoped token gets **403 with
`WWW-Authenticate: Bearer error="insufficient_scope", scope="pensieve:read"`** — 403 rather than 401 per
RFC 6750 §3.1, because re-presenting the same credential would fail identically and a client that reads
401 as "start the OAuth dance" would loop. Setting `MCP_OAUTH_REQUIRED_SCOPES=""` turns the check off; it
is the escape hatch if a connector cannot be granted the scope, and it leaves audience as the only gate.
The sidecar also pins `algorithms: ["RS256"]` on `jwtVerify` (`src/auth.ts`).

## Verify

```bash
# token with the right claims (password grant for scripting; authcode+PKCE yields the same token).
# NOTE: request only scope=openid — pensieve:read/email are default client scopes ON THIS CLIENT and
# attach automatically; requesting a default scope by name is rejected by Keycloak as invalid_scope.
curl -s -X POST http://localhost:8081/realms/pensieve/protocol/openid-connect/token \
  -d client_id=pensieve-test-client -d grant_type=password \
  -d username=seth -d password=password -d 'scope=openid' | jq -r .access_token
# decode it: aud == http://localhost:8090/mcp, scope has pensieve:read, email + sub present, RS256
```

> ⚠️ **This is the dev realm and it does not generalize to production.** `pensieve-test-client` does not
> exist in prod (no public client, no direct-access grants), so there is no password grant to script with
> — drive a real authorization-code flow instead. And `scope=openid` only picks up `pensieve:read` because
> the *client* lists it as a default scope; in prod that is true of `pensieve-web` and of nothing else
> until you attach it. If a prod token comes back with no `aud`, the client is missing the scope — see
> [Audience segmentation](#audience-segmentation-prod-only).

Or drive an interactive authcode + PKCE flow with `npx @modelcontextprotocol/inspector`.

## Account emails

Password reset is a **Keycloak flow end to end** — the API and the web app hold no code for it. An
account's email address exists **for password reset and admin action emails, and nothing else**; it is
not a gate on signing in:

- **`resetPasswordAllowed: true`** — puts "Forgot Password?" on the hosted login page, which emails a
  reset link. Registration stays disabled, so this is the only self-service credential path.
- **`verifyEmail: false`** — deliberate, in **both** the dev and prod realms. An account signs in
  whether or not its address has been confirmed; Keycloak sends no confirmation mail and holds no
  session at a "verify your email" page.

**What `verifyEmail: false` still leaves you responsible for.** Keycloak keeps a per-account
`emailVerified` flag and stamps it into the token as the `email_verified` claim; turning the realm gate
off only means Keycloak never sets that flag on its own. The API reads the claim in exactly one place —
`OwnerResolver`'s **claim-by-email** path, which links a login to a pre-existing `users` row that has no
`keycloak_sub` yet (the [admin bootstrap](../documentation/DevDocumentation.md#bootstrap-claim-the-seeded-default-showcase-row)
is the case that matters). The guard is unchanged and still correct: an unverified address must not take
over an existing row. So an admin-created account that needs to claim a row must have its address marked
verified **explicitly** — admin console → the user → *Email verified* → On, or the
`execute-actions-email` call below, which verifies the address as a side effect of onboarding. An
account that only ever JIT-provisions its own fresh row needs none of this.

None of it works without SMTP. Dev points `smtpServer` at the **`mailpit`** container (`mailpit:1025`, no
auth) so nothing leaves the machine — read the captured mail at <http://localhost:8025>. Production
resolves `PENSIEVE_SMTP_*` from the service environment (`SMTP_*` in `.env`, see
`../dockerCompose/.env.production.example`); `STARTTLS`/`SSL` are a pair chosen by port (587 → STARTTLS, 465 → SSL).

**Onboarding an admin-created account:** since registration is off, send one email that lets the user
pick their own password — no temporary password to hand over. Keeping `VERIFY_EMAIL` in the action list
is still worthwhile even though logins no longer require it: completing it sets the account's
`emailVerified` flag, which is what claim-by-email needs (above), and it confirms the address is
reachable before it is the only password-reset route the user has.

```bash
curl -X PUT "$KC/admin/realms/pensieve/users/$USER_ID/execute-actions-email?client_id=pensieve-web&redirect_uri=https://$APP_DOMAIN" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '["VERIFY_EMAIL","UPDATE_PASSWORD"]'
```

Admin REST calls like this one need only the bearer token — the API paths are deliberately outside the
Caddy gate, because a request has exactly one `Authorization` header and it is already spent on the
bearer (see [Admin console access](#admin-console-access)). Minting `$ADMIN_TOKEN` is the one call that
*does* need the gate credential, because the master realm's token endpoint sits behind it and a token
request carries no `Authorization` header of its own:

```bash
ADMIN_TOKEN=$(curl -s -u "$KC_ADMIN_UI_USER:$KC_ADMIN_UI_PASSWORD" \
  -X POST "$KC/realms/master/protocol/openid-connect/token" \
  -d client_id=admin-cli -d grant_type=password \
  -d "username=$KC_ADMIN_USER" -d "password=$KC_ADMIN_PASSWORD" | jq -r .access_token)
```

(Locally, neither `-u` applies — the dev Keycloak is not behind Caddy.)

**Link lifespans:** a user-initiated reset link lives **15 minutes**
(`actionTokenGeneratedByUserLifespan: 900`, raised from Keycloak's 5-minute default — five minutes is
short enough that a user who checks mail on another device routinely arrives at an expired link). The
admin-sent action email keeps the 12-hour default (`actionTokenGeneratedByAdminLifespan`).

### Interaction with tests

The test Keycloak imports this same dev realm. `KeycloakTestSupport.ensureUser` marks the accounts it
creates verified, so their tokens carry `email_verified: true` and every claim-by-email test behaves as
it always has. The one test that needs the opposite claim
(`AuthSecuredProfileTests.tokenWithUnverifiedEmail_MatchingExistingAccount_DoesNotClaimIt`, covering
`OwnerResolver`'s claim-by-email guard) creates its user unverified and calls `passwordGrantUnverified`
— now a plain password grant, because the realm no longer refuses one for an unverified account. It is
kept as a named method so the call site still reads as "a token with an unverified email", and so there
is one place to change if the realm ever gates logins again. The dev realm's `mailpit` SMTP host does
not resolve inside the Testcontainers Keycloak, which is harmless: no test triggers a send.

## Admin console access

In production Caddy publishes all of `AUTH_DOMAIN`, which would put Keycloak's admin login on the open
internet. The `Caddyfile` puts a **basic-auth prompt in front of the console pages (`/admin`,
`/admin/master/console/*`) and the master realm (`/realms/master/*`)** — a second credential
(`KC_ADMIN_UI_USER` / `KC_ADMIN_UI_PASSWORD_HASH` in `.env`, hash from `caddy hash-password`) layered
on top of the Keycloak admin password itself.

Paths deliberately left open — adding any of them to the matcher will break the site:

- **`/realms/pensieve/*`** — authorize, token, JWKS, `.well-known`. Every login and every token depends
  on these being anonymous.
- **`/resources/*`** — static assets for the admin console *and* the public login pages. Gate it and
  real users get an unstyled login screen.
- **The Admin REST API (`/admin/realms/*`, `/admin/serverinfo`, …)** — the matcher was originally all
  of `/admin/*`, and that can never work: the console SPA calls these routes with
  `Authorization: Bearer <admin token>`, a request has exactly one `Authorization` header, and so a
  basic-auth gate there is unsatisfiable — the browser's credential popup loops forever regardless of
  what is typed (found 2026-08-17, the first time the console got past the framing fix; the fix rode
  the release after 1.0.1). These routes are not open in any meaningful sense: Keycloak requires a
  valid admin bearer token on every one of them.

Consequences worth knowing:

- Logging into the console prompts the gate **twice** in a fresh browser session: once for the console
  page, and usually once more after Keycloak's login redirects back and the SPA starts calling the API
  (browsers don't always replay basic-auth credentials across that hop). Same `KC_ADMIN_UI_*`
  credentials both times; there is no third credential.
- **Scripted Admin REST calls need only the bearer token.** The exception is *minting* that token —
  the master realm's token endpoint is behind the gate and a token request has a free `Authorization`
  header, so that one call takes `-u` — see [Onboarding an admin-created account](#account-emails).
- Basic auth is a scanning barrier, not a substitute for a strong Keycloak admin password. Enable OTP
  on the admin account too (Account → Signing in → Two-factor authentication).

`KC_BOOTSTRAP_ADMIN_USERNAME` / `_PASSWORD` apply **only on the first boot** with an empty
`keycloak-db`; changing them later has no effect. After the first deploy, create a real admin account
with OTP, delete the bootstrap one, and blank those values in `.env`.

## Offline tokens for MCP connectors

A remote MCP connector is authorized once in a browser and then used in bursts with long gaps. An
ordinary refresh token is bound to the user's **SSO session** (`ssoSessionIdleTimeout` 30 min,
`ssoSessionMaxLifespan` 10 h), so without `offline_access` a connector dies after 30 idle minutes —
and because the SSO session is shared per user per realm, **logging out of the web app kills the
connector too**. An offline token is bound to an offline session instead, which is why it is the
right grant here.

The prod realm bounds those offline sessions two ways (Keycloak's default is unbounded):

- **`offlineSessionIdleTimeout` — 10 days.** A connector nobody touches for 10 days expires. Any use
  resets the clock, so this only reaps abandoned authorizations.
- **`offlineSessionMaxLifespan` — 30 days.** A hard ceiling regardless of use: even a connector in daily
  use needs re-authorizing monthly.

So an actively-used connector lives 30 days; an idle one dies at 10.

`offline_access` is a realm *optional* scope, so it is only issued when a client is granted it. When
pre-registering a connector client via the admin console:

1. **Add `pensieve:read` as a *default* client scope.** It is not a realm default in production (see
   [Audience segmentation](#audience-segmentation-prod-only)), so a freshly registered client gets none of
   it: no `pensieve:read` in the token's `scope`, and — because the audience mapper lives on that scope —
   no `aud` either. The sidecar refuses such a token twice over, first on audience and then on scope. This
   is the step that is easy to miss, because nothing about the client's own settings hints at it.
2. Add `offline_access` to that client's scopes. If the client requests the scope itself, adding it as
   an **optional** scope is enough; if it does not, add it as a **default** scope so Keycloak issues it
   unconditionally — then confirm by decoding the resulting token rather than assuming.
3. **Decode the token before declaring victory.** Check `aud == https://<MCP_DOMAIN>/mcp`, that `scope`
   contains `pensieve:read`, and that `typ` is `Offline` on the refresh token. Each of the three failure
   modes looks identical from the client's side — "it stopped working" — and they are fixed in different
   places.
4. **The connector must persist the rotated refresh token.** Production runs `revokeRefreshToken: true`
   with `refreshTokenMaxReuse: 0`, so a connector that re-presents the token it used last time has its
   whole offline session revoked, not merely that request refused.
5. Leave `pensieve-web` alone. The BFF has a human present and should keep short online sessions;
   `offline_access` stays optional-and-unrequested there.

Note that the sidecar advertises only `pensieve:read` in its protected-resource metadata
(`MCP_OAUTH_SCOPES`, default in `the-game-pensieve-mcp/src/config.ts`), so a client that derives its
scope request from RFC 9728 metadata will not ask for `offline_access` on its own. Adding it there is
possible but a category error — `scopes_supported` describes scopes for reaching *the resource*, and
`offline_access` is an authorization-server grant modifier. Granting it on the client is the clean fix.

## Notes / next (Phase 4+)

- **Issuer / hostname (solved):** `KC_HOSTNAME=http://localhost:8081` pins a **canonical** `iss`
  (`http://localhost:8081/realms/pensieve`) regardless of how Keycloak is reached, and
  `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true` lets containers fetch JWKS over the internal network. So
  validators (sidecar, API) use: expected `iss` = `http://localhost:8081/realms/pensieve`, JWKS URI =
  `http://keycloak:8080/realms/pensieve/protocol/openid-connect/certs`, expected `aud` =
  `http://localhost:8090/mcp`. Verified: a token minted via the internal `keycloak:8080` path still
  carries the canonical `iss`.
- **Audience** is currently `http://localhost:8090/mcp` (dev). In prod it becomes the public
  `https://…/mcp` URL — update the Audience mapper's `included.custom.audience` in the realm import.
- **DCR for remote hosts** (claude.ai): Trusted Hosts can't easily scope to Anthropic's registration
  source — pre-register a client instead, or adopt CIMD (`--features=cimd`, experimental) later.
- This is a **dev** setup (`start-dev`, HTTP, admin/admin). Production hardening (HTTPS via Caddy,
  real admin creds, a managed DB) is Phase 6.
