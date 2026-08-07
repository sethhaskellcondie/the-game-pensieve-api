# Keycloak — MCP Authorization Server (self-hosted)

Self-hosted Keycloak (**26.7**) that acts as the OAuth 2.1 Authorization Server for the MCP rollout
(Phase 3 of `../localFiles/mcp_rollout.md`). Runs as the `keycloak` service in both development compose
files, `../compose.unsecured.yaml` and `../compose.secured.yaml` (the second includes the first and only
switches the backend to the `secured` profile — the Keycloak service is identical in each).

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
  `../compose.production.yaml`. Derived from the dev realm with the dev-only surface removed:
  **no test users**, **no `pensieve-test-client`** (no public client, no direct-access grants), **no
  anonymous DCR** (the Trusted Hosts policy is absent, so Keycloak's default policies deny anonymous
  registration — pre-register remote MCP hosts via the admin console instead), and
  `sslRequired=external` (TLS terminates at Caddy). Deployment-specific values are `${...}`
  placeholders (`PENSIEVE_APP_DOMAIN`, `PENSIEVE_MCP_DOMAIN`, `PENSIEVE_WEB_CLIENT_SECRET`,
  `PENSIEVE_SMTP_*`) that
  Keycloak resolves from the service environment at import time — after the first boot, decode a
  token and verify `aud`; a literal `${PENSIEVE_...}` means substitution failed. If you change the
  dev realm, re-apply the equivalent change to the prod file.

## Bring it up

```bash
# from the repo root — imports the realm on first boot. Either development compose file works;
# they define the same keycloak service.
docker compose -f compose.unsecured.yaml up -d keycloak
```

Admin console: <http://localhost:8081> (admin / admin — dev only). Realm discovery:
<http://localhost:8081/realms/pensieve/.well-known/openid-configuration>.

The realm config lives entirely in the import file, so a clean `docker compose -f compose.unsecured.yaml
down -v` + `up` rebuilds it exactly (no manual step). Edit `import/pensieve-realm.json` to change it.
**`--import-realm` only imports into an empty volume**, so editing the file does nothing to a Keycloak
that has already booted once — a stale `keycloak_data` volume is the usual reason a realm change, or a
client the realm is supposed to ship, appears to be missing.

## Verify

```bash
# token with the right claims (password grant for scripting; authcode+PKCE yields the same token).
# NOTE: request only scope=openid — pensieve:read/email are default client scopes and attach
# automatically; requesting a default scope by name is rejected by Keycloak as invalid_scope.
curl -s -X POST http://localhost:8081/realms/pensieve/protocol/openid-connect/token \
  -d client_id=pensieve-test-client -d grant_type=password \
  -d username=seth -d password=password -d 'scope=openid' | jq -r .access_token
# decode it: aud == http://localhost:8090/mcp, scope has pensieve:read, email + sub present, RS256
```

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
`../.env.production.example`); `STARTTLS`/`SSL` are a pair chosen by port (587 → STARTTLS, 465 → SSL).

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
