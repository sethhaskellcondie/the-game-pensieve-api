# Keycloak — MCP Authorization Server (self-hosted)

Self-hosted Keycloak (**26.7**) that acts as the OAuth 2.1 Authorization Server for the MCP rollout
(Phase 3 of `../localFiles/mcp_rollout.md`). Runs as the `keycloak` service in `../compose.yaml`.

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
     must be localhost; the sender-IP check is disabled so requests through the Docker bridge work).
- `import-prod/pensieve-realm.json` — the **production** realm, mounted by
  `../compose.production.yaml`. Derived from the dev realm with the dev-only surface removed:
  **no test users**, **no `pensieve-test-client`** (no public client, no direct-access grants), **no
  anonymous DCR** (the Trusted Hosts policy is absent, so Keycloak's default policies deny anonymous
  registration — pre-register remote MCP hosts via the admin console instead), and
  `sslRequired=external` (TLS terminates at Caddy). Deployment-specific values are `${...}`
  placeholders (`PENSIEVE_APP_DOMAIN`, `PENSIEVE_MCP_DOMAIN`, `PENSIEVE_WEB_CLIENT_SECRET`) that
  Keycloak resolves from the service environment at import time — after the first boot, decode a
  token and verify `aud`; a literal `${PENSIEVE_...}` means substitution failed. If you change the
  dev realm, re-apply the equivalent change to the prod file.

## Bring it up

```bash
docker compose up -d keycloak          # from the repo root — imports the realm on first boot
```

Admin console: <http://localhost:8081> (admin / admin — dev only). Realm discovery:
<http://localhost:8081/realms/pensieve/.well-known/openid-configuration>.

The realm config lives entirely in the import file, so a clean `docker compose down -v` + `up`
rebuilds it exactly (no manual step). Edit `import/pensieve-realm.json` to change it.

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
