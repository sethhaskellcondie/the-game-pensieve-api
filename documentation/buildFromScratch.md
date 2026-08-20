# Build From Scratch — the production host

This is the operations runbook for the production server: every account, provider, and provisioning
step needed to build — or rebuild — the host from nothing, with the results recorded as each step was
actually performed. The launch was originally driven from a working plan in `localFiles/`, which is
untracked and does not survive a clone; **this file is the durable record.** Steps below are marked
✅ done (with what happened) or ⬜ not yet done (with exactly what to do).

Division of labor with the other docs:

- [`DevDocumentation.md`](DevDocumentation.md) — the code, the local dev environment, and the
  production *topology* (compose file, Caddyfile, edge posture, how the deploy scripts work).
- [`dockerComposeExplainer.md`](dockerComposeExplainer.md) — every compose file and env file.
- [`scriptExplainer.md`](scriptExplainer.md) — every script in `scripts/`, including the deploy pair.
- **This file** — the *host* the topology runs on: providers, the Droplet, provisioning, first
  bringup, backups, and the operational rules that only exist on the box.

---

## ⚠️ The one-way doors

Everything below is arranged around four things that cannot be undone. Read this section before
touching the production host, every time.

1. **The Keycloak realm import runs exactly once**, on first boot with an empty `keycloak-db`. Every
   value in `keycloak/import-prod/pensieve-realm.json` — password policy, refresh-token rotation, the
   SMTP block, `OIDC_CLIENT_SECRET`, the audience mapper — is baked at that moment. Editing `.env` or
   the JSON afterwards changes nothing; correcting a mistake means destroying the volume, which
   destroys every user account.
2. **`keycloak_db_data` is irreplaceable.** `users.keycloak_sub` is a UNIQUE foreign reference into
   it. Lose the volume and every `sub` changes: existing users are orphaned from their collections and
   silently JIT-provision empty new rows. **Never run `down -v` on the production host.**
3. **Published image tags are immutable.** `:X.Y.Z` on Docker Hub is permanent. A fix after publish
   is a new version.
4. **`compose.production.yaml` and the `Caddyfile` are checked out at the version tag** on the host —
   they are not runtime configuration. Changing them means a new release.

---

## Accounts and providers

**Domain**

- Registrar is `Porkbun`
- DNS host is `Porkbun (registrar-provided DNS)`
- Apex domain is `sethcondie.com`
- Registered on `2026-08-13`

**Hostnames.**

- APP_DOMAIN=pensieve.sethcondie.com
- MCP_DOMAIN=mcp.pensieve.sethcondie.com
- AUTH_DOMAIN=auth.pensieve.sethcondie.com
- APP_ALIAS_DOMAIN=pensive.sethcondie.com  *(typo alias, added 2026-08-20 — 301s to APP_DOMAIN)*

The first three are permanent. `AUTH_DOMAIN` is the token issuer, `MCP_DOMAIN` is the `aud` claim,
and both are baked into three services and every issued token — there is no rename path.

`APP_ALIAS_DOMAIN` is the opposite: pure edge config, in the Caddyfile and nowhere else, changeable
or removable in any release. It exists because `pensieve` gets typed `pensive`, and it is a redirect
and never a second origin — see step 10.

**Email relay — Resend**

- Account email is `8bitdad7dc@gmail.com`
- Verified domain is `pensieve.sethcondie.com`
- Subdomain is verified and will be used, over the apex domain, because if a reputation issue comes
  up with the subdomain it can be changed in the future. (With some manual syncing with the keycloak
  realm and this project.)
- Verified on `2026-08-13`
- SMTP_FROM is `no-reply@pensieve.sethcondie.com`
- Replies to that address will be discarded
- First real send (2026-08-14, the launch rehearsal): Resend showed green **delivered**, but Yahoo
  placed the message in **spam** — expected for a fresh sending domain with no reputation and DMARC
  at `p=none`. The recipient marked it not-spam. "Delivered" only means the receiving server accepted
  the message; recheck inbox placement once real users exist, because a password-reset link in spam
  is a locked-out user (self-service reset is the only credential-recovery path).
- DNS records published (DKIM `TXT`, SPF, `MX` for bounces, DMARC) on `2026-08-13`

`SMTP_FROM` is effectively frozen. Keycloak resolves it into the realm at first import and never reads
`.env` for it again, so changing it later means editing the live realm by hand — at which point `.env`
and the realm disagree. Pick the address users should see, then verify whichever domain permits it.

Public SMTP settings — confirmed against the Resend dashboard 2026-08-13; port changed 465 → 2465 on
2026-08-17 (see step 5 below — DigitalOcean blocks the standard ports and denied the unblock ticket):

- SMTP_HOST=smtp.resend.com
- SMTP_PORT=2465
- SMTP_USER=resend (literally the string `resend`)
- SMTP_STARTTLS=false
- SMTP_SSL=true
- SMTP_PASSWORD is the Resend API key, sending-access only — password manager only, never here and
  never in git. Unlike `SMTP_FROM`, this one is cheap to rotate: revoke, regenerate, update `.env`,
  restart Keycloak.

Port and TLS flags are a **pair**, never mixed — `465/2465 → STARTTLS=false, SSL=true` (implicit TLS,
what we use); `587/2587 → STARTTLS=true, SSL=false`. Resend listens on 25, 465, 587, 2465, and 2587 at
the same time, so the port is purely a client-side choice with nothing to configure on their end —
2465 is byte-for-byte identical to 465 except the TCP port. Mixing the pair fails at connect time with
a TLS handshake error that does not name the port as the cause.

**DNS zone hazard, learned the hard way:** Porkbun installs a **wildcard `*` CNAME** to its parking
page on every new domain. It was deleted during setup. With it in place *every* name in the zone
resolves, so `NXDOMAIN` disappears as a signal and no DNS check can distinguish "record correct" from
"record missing." If the zone is ever rebuilt, delete the wildcard again first. Two
`_acme-challenge.sethcondie.com` TXT records also exist from Porkbun's free-SSL feature — harmless
(Caddy uses HTTP-01, not DNS-01), do not be confused by them.

**Hosting — DigitalOcean**

- Droplet name / hostname: `pensieve-project` (created with a typo — `pensive-project`, missing an
  "e"; the dashboard name and the box's hostname/`/etc/hosts` were both corrected 2026-08-17.
  Cosmetic only: SSH goes by IP, DNS by IP, and the compose project name is pinned — nothing
  functional ever referenced the hostname)
- Region / size: `nyc1` / 4 GB RAM, Ubuntu LTS (~$24/mo)
- Public IP: `159.203.179.41` (also the value the four A records point at)
- Auth: SSH key only; password login disabled at creation. Additional machines get access by
  appending their own public key to `~/.ssh/authorized_keys` (and updating the firewall's port-22
  source) — never by copying the private key around.
- The workstation reaches it as `ssh pensieve-prod` — a `Host pensieve-prod` alias in
  `~/.ssh/config` (User root, the IP above, `IdentityFile ~/.ssh/id_ed25519`, `AddKeysToAgent yes`,
  `UseKeychain yes`). The key is passphrase-protected; the passphrase lives in the macOS Keychain
  via a one-time `ssh-add --apple-use-keychain ~/.ssh/id_ed25519`, which is what lets the deploy
  script's `BatchMode=yes` connections work without a prompt. This alias is the deploy scripts'
  default `DEPLOY_HOST`.

**Why 4 GB.** The stack's steady-state working set is ~2.3 GB before the OS: backend JVM ~700 MB,
Keycloak ~800 MB, two Postgres ~200 MB each, Next.js ~200 MB, sidecar ~100 MB, Caddy ~50 MB. The
compose file caps every service (`mem_limit`/`cpus`, ~3.9 GB of ceilings) so one runaway container
cannot take down the rest. 2 GB cannot hold the working set; 8 GB buys nothing.

---

## Provisioning — the build order

Performed 2026-08-14 onward. Each step records what was actually done, so a rebuild can follow this
top to bottom.

### ✅ 1. Create the Droplet

4 GB Ubuntu LTS, SSH-key auth chosen at creation (which disables password login). Verify access:
`ssh root@<droplet-ip>`.

### ✅ 2. Swap file — 2 GB, persistent

Non-negotiable: Keycloak's startup is memory-hungry enough to OOM-kill a neighbour without it.

```bash
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
free -h    # Swap: must show 2.0Gi
```

### ✅ 3. Docker Engine and Compose plugin

Installed from Docker's official repository (not Ubuntu's `docker.io` package):

```bash
curl -fsSL https://get.docker.com | sh
docker run hello-world
docker compose version
```

### ✅ 4. Firewall — DO cloud firewall, attached to the Droplet

| Direction | Rule | Source / Destination |
|---|---|---|
| Inbound | TCP 22 (SSH) | workstation IP only _(record the IP here)_ |
| Inbound | TCP 80 | all IPv4 / all IPv6 |
| Inbound | TCP 443 | all IPv4 / all IPv6 |
| Outbound | ICMP, all TCP, all UDP | all (DO defaults) |

Deliberately **cloud firewall only, no `ufw`** — one place to update, not two. When the workstation's
IP changes, SSH times out; the fix is editing the port-22 source in the DO dashboard (browser), with
the Droplet's recovery console as the fallback. Open outbound is required: the host reaches Docker
Hub, GitHub, Let's Encrypt, and Resend.

### ✅ 5. Outbound SMTP to Resend — **resolved via port 2465** (DO ticket denied)

```bash
nc -vz -w 5 smtp.resend.com 2465
```

**History.** Port 465 timed out 2026-08-14: DigitalOcean blocks outbound SMTP (25/465/587) on newer
accounts. Support ticket submitted 2026-08-14 was **denied 2026-08-15** — DO would not lift the block.

**Resolution 2026-08-17.** Resend also listens on 2465 (implicit TLS) and 2587 (STARTTLS), which sit
outside DO's block. Both connected from the Droplet on the first try. The deployment now uses **2465**,
which keeps the exact TLS flags proven in the launch rehearsal (`STARTTLS=false, SSL=true`) — the only
value that changed anywhere is `SMTP_PORT`. Updated the same day in `.env.production.example`,
`.env.rehearsal`, and the password-manager copy of the production values.

This was a hard gate for the first deploy (the realm import bakes the SMTP config and runs once); it
is now clear. If this check is ever re-run on a rebuild, test the port actually used (2465) — a result
on any other port proves nothing.

### ⬜ 6. Unattended security upgrades

```bash
apt install -y unattended-upgrades
dpkg-reconfigure -plow unattended-upgrades   # answer Yes
```

### ⬜ 7. Docker daemon log rotation

A 4 GB disk fills faster than you expect, and a full disk takes down both databases and Caddy's cert
store at once. Write `/etc/docker/daemon.json`:

```json
{ "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "3" } }
```

Then `systemctl restart docker`.

### ⬜ 8. Docker Hub login

```bash
docker login -u sethcondie    # paste the ACCESS TOKEN found in the password manager, never the account password
```

Anonymous pulls are rate-limited; hitting the limit mid-deploy is a miserable way to find out.

### ⬜ 9. Read-only GitHub deploy key and clone

On the Droplet: `ssh-keygen -t ed25519 -C "pensieve-droplet-deploy"`, add the **public** key in
GitHub → this repo → Settings → Deploy keys, **read-only** (leave "Allow write access" unchecked).
GitHub key's name: pensieve-droplet-deploy. Then:

```bash
git clone git@github.com:sethhaskellcondie/the-game-pensieve-api.git /opt/pensieve
```

`/opt/pensieve` is the exact path the deploy scripts assume.

### ⬜ 10. DNS — point the four A records at the Droplet

In Porkbun, create A records for `pensieve.sethcondie.com`, `mcp.pensieve.sethcondie.com`,
`auth.pensieve.sethcondie.com`, and `pensive.sethcondie.com`, all pointing at the Droplet's public
IP. Verify **from off-network** (a machine that is not the workstation — e.g. a phone off wifi):

```bash
dig +short pensieve.sethcondie.com          # → the Droplet IP
dig +short mcp.pensieve.sethcondie.com      # → the Droplet IP
dig +short auth.pensieve.sethcondie.com     # → the Droplet IP
dig +short pensive.sethcondie.com           # → the Droplet IP  (typo alias, added 2026-08-20)
```

The fourth is the **typo alias**: `pensive` without the second "e", which is how the domain gets
misspelled in practice. It is not a fourth way into the app — Caddy answers it with a 301 to
`pensieve.sethcondie.com`, path and query intact (`APP_ALIAS_DOMAIN` in the env, alias site block in
the Caddyfile). It gets its own Let's Encrypt certificate, so **the A record has to exist before the
release carrying the Caddyfile change is deployed**; deploy first and ACME fails for that host alone
while the other three keep serving, and Caddy retries on its own once DNS appears.

Never add the alias to `APP_ORIGIN`, to the `pensieve-web` redirect URIs, or to Keycloak's allowed
origins. Reaching the app *through* the alias would scope session cookies to the wrong host and
break login with no visible error; the 301 is what guarantees nobody does.

Caddy's ACME certificate challenge runs on first boot; without resolving DNS there are no
certificates and nothing serves. (And remember the wildcard-CNAME hazard above — with a wildcard in
the zone this verification is worthless.)

### Provisioning exit criteria

SSH works · `docker run hello-world` works · `/opt/pensieve` is a clean checkout · `free -h` shows
the 2 GB swap · all four hostnames resolve to this box from outside · `nc -vz -w 5 smtp.resend.com
2465` connects (step 5).

---

## ⬜ Configure the environment (`.env`)

The SMTP gate (step 5) is resolved; nothing blocks this section.

1. `cp /opt/pensieve/dockerCompose/.env.production.example /opt/pensieve/dockerCompose/.env` and fill
   **every** value. `nano /opt/pensieve/dockerCompose/.env` It must sit beside the compose file — that is where compose autoloads it from.
   A complete file-shaped draft already exists in the password manager (saved 2026-08-14) —
   transcribe from it, don't re-derive.
2. Generate each secret with `openssl rand -base64 48`: `SESSION_SECRET` (≥32 chars — enforced at
   boot), `OIDC_CLIENT_SECRET`, `POSTGRES_PASSWORD`, `KC_DB_PASSWORD`, `KC_ADMIN_PASSWORD`.
3. ~~`KC_ADMIN_UI_PASSWORD_HASH` from `caddy hash-password`~~ — **gone since 2026-08-18**: the
   basic-auth gate was removed with its variables (see the admin-console saga below and in
   `PastIssues.md`). The console's protection is Keycloak-side: strong admin password, enforced
   OTP, and master-realm brute-force detection — the last one is runtime realm config that a fresh
   `keycloak-db` reverts to OFF, and the deploy script re-asserts it automatically on any deploy
   where `KC_ADMIN_*` are still set in `.env` (i.e. right after a bootstrap; see
   `scriptExplainer.md` step 7). Only a rebuild that never runs the deploy script needs the hand
   version (Realm settings → Security defenses, 10 failures, temporary).
4. **Use the exact SMTP values proven in the rehearsal — copy from the password manager, do not
   retype from memory.**
5. `chmod 600 dockerCompose/.env`. Store the final copy back in the password manager — **this file
   exists in exactly one place.**
6. Re-read the one-way-doors section at the top. The next command is the door.

If any variable is ambiguous or confusing while filling it in, fix
`dockerCompose/.env.production.example` and `dockerComposeExplainer.md` immediately — the first
cold read of that file is unrecoverable once you know the answers.

---

## ⬜ First deploy, by hand

**Do not start until outbound SMTP (step 5 above) is confirmed working.** By hand, not with the
script — you cannot script a procedure you have not performed. **Keep a live transcript**: paste
every command and its output into a scratch file as you go. This happens exactly once, and the
transcript is the specification for verifying `deploy-production-remote.sh` afterward.

```bash
cd /opt/pensieve
git fetch --tags && git checkout v1.0.0     # confirm the three image pins read 1.0.0, not latest
docker compose -f dockerCompose/compose.production.yaml up -d
```

**Any `variable is not set` warning ⇒ stop.** (The `:?` guards abort on blank secrets rather than
booting insecure.) Then `docker compose -f dockerCompose/compose.production.yaml logs -f` — Keycloak's
realm import (30–60s) and the backend's Flyway run dominate first boot; be generous with timeouts.
Confirm Caddy obtained certificates for all three hostnames — ACME failures here are almost always
DNS not resolving yet.

### Verification checklist (the rehearsal's assertions, now against real DNS and real TLS)

- [x] **No literal `${PENSIEVE_…}` survived substitution** anywhere in the realm — the highest-value
      check. Verified 2026-08-17: kcadm sweep of the realm, clients, and client-scopes found zero
      `PENSIEVE_` strings; the client secret matches `OIDC_CLIENT_SECRET`; the `redirectUris` were
      also proven externally (Keycloak redirects errors *back to* the callback URL, which it only
      does for a registered URI). A literal placeholder means wiping `keycloak-db` and re-importing.
- [x] Decode a real access token: `aud == https://mcp.pensieve.sethcondie.com/mcp` and
      `iss == https://auth.pensieve.sethcondie.com/realms/pensieve`. In this BFF the token never
      reaches a browser — the equivalent proof happens during bootstrap: after first login,
      `/api/auth/session` reporting a real role means the secured backend accepted `aud` and `iss`
      (`"unknown"` is exactly what a bad audience mapper produces). Verified 2026-08-17: the
      bootstrap login claimed the showcase row and the session reported role ADMIN.
- [x] `https://pensieve.sethcondie.com/api/heartbeat` → `.status == "online"` **and
      `.secureMode == true`**. Verified 2026-08-17.
- [x] `https://mcp.pensieve.sethcondie.com/healthz` → 200; tokenless `POST /mcp` → 401 with a
      `WWW-Authenticate` challenge; anonymous dynamic client registration refused — as a **403**
      from the Trusted Hosts policy (`Host not trusted`), not the 401 the rehearsal predicted; the
      policy refuses before auth is even considered. Verified 2026-08-17.
- [x] The Caddy gate, **both directions**: `/admin` prompts for basic auth;
      `/realms/pensieve/.well-known/openid-configuration` returns 200 with no prompt; the login page
      renders **styled** (unstyled = the matcher is too broad; the page's three `/resources/…` CSS
      files each returned 200 `text/css`). Verified 2026-08-17. Note: the authorization endpoint
      refuses a request without PKCE (`Missing parameter: code_challenge_method`) — correct
      behavior, not a defect. **(Superseded 2026-08-18: the gate was removed entirely — see the
      admin-console saga below. On a re-run, the expectation is inverted: nothing on the auth host
      answers a Basic challenge, and the console page loads straight to Keycloak's login.)**
- [x] Every service except Caddy publishes no host port. Verified 2026-08-17 via `docker ps`.
      **One deliberate exception added later the same day:** the app database (`db`) publishes
      `127.0.0.1:5432` — loopback only, unreachable from the internet — for IDE access over an SSH
      tunnel. See "Connecting an IDE to the production database" below. When re-running this check,
      the expected `docker ps` output is Caddy on `0.0.0.0:80/443` and db on `127.0.0.1:5432` and
      nothing else.
- [x] Realm settings took: brute force on, password policy, session timeouts — verified 2026-08-17
      via kcadm. "A login under Events" can only exist after the bootstrap login; check it then.

### Bootstrap the admin

Procedure in
[`DevDocumentation.md`](DevDocumentation.md#bootstrap-claim-the-seeded-default-showcase-row):

> **Surprise found 2026-08-17, during this step:** at `v1.0.0` the Keycloak **admin console does not
> work through Caddy** — the console frames its own origin for a third-party-cookie check, and the
> edge's `frame-ancestors 'none'` blocks it ("Timeout when waiting for 3rd party check iframe
> message"). The Caddyfile fix (framing headers split out of `security_headers`; the auth host relies
> on Keycloak's own `frame-ancestors 'self'`) rides release **1.0.1**. Until it is deployed, all
> admin work goes through `kcadm` inside the container — the account below was created that way:
>
> **Surprise #3, found the same evening once 1.0.1's framing fix was live:** the console got further
> and then hit an **unpassable basic-auth popup** — the Caddy gate originally covered all of
> `/admin/*`, but the console SPA calls the Admin REST API with `Authorization: Bearer`, a request
> has exactly one `Authorization` header, and so the gate on those routes can never be satisfied
> (send Basic → Keycloak rejects the missing bearer; send Bearer → Caddy rejects the missing Basic;
> the popup loops forever). Diagnosed from DevTools: `GET /admin/serverinfo` → 401,
> `WWW-Authenticate: Basic realm="restricted"`, served by Caddy. The fix narrows the gate to the
> console pages + `/realms/master/*`, leaving the Admin REST API to Keycloak's own bearer
> enforcement; `prod-rehearsal.sh` gained a check (24 now) asserting the API's refusal is NOT a
> Basic challenge. Rode release 1.0.2 (2026-08-18) — **but deploying it surfaced surprise #4: the
> stale-inode bind mount** (deploy green, edge still serving the old matcher, caddy "Up 19 hours";
> fixed live with a hand `up -d --force-recreate caddy`, and permanently in the deploy script — see
> the Prove-the-deploy-script section and `PastIssues.md`). Full write-up in `PastIssues.md`.
>
> **Matcher iterations three and four, next day (2026-08-18):** with the collision fix live (1.0.2,
> after the stale-mount recreate), the console *worked* but threw stray gate popups mid-session —
> the remaining gated `/realms/master/*` endpoints (token refresh, session iframes) are background
> fetches where browsers don't replay basic credentials, and canceling the token-refresh prompt
> logs the admin out. Round three (1.0.3) narrowed the gate to the interactive login surface. Then
> round four: the console's navigation menu vanished for every account — the `/admin/master/console/*`
> wildcard was swallowing the console's own runtime API (`config`, `whoami`; the menu is built from
> `whoami`, a Bearer fetch — the collision again, one segment deeper).
>
> **Final resolution, 2026-08-18: the gate was removed entirely.** Four rounds of a basic-auth layer
> fighting a Bearer-authenticated SPA was the design telling us no. Protection is Keycloak-side and
> was put in place *before* the removal shipped: strong unique admin password, **OTP enforced via
> the `CONFIGURE_TOTP` required action** on the admin account, and **brute-force detection enabled
> on the master realm** (10 failures, temporary — applied live via the Admin API; it is runtime
> realm config that a fresh `keycloak-db` reverts, so the deploy script re-asserts it whenever
> `.env` still carries the `KC_ADMIN_*` bootstrap credentials — the credential window and the risk
> window coincide). The `KC_ADMIN_UI_*` variables left the
> compose file, the env example, and the rehearsal with it. All four rounds are written up in
> `PastIssues.md`; the rehearsal (25 checks) pins the gate's *absence* — nothing on the auth host
> may answer a Basic challenge.
> `kc create users -r pensieve -s username=… -s email=… -s emailVerified=true -s enabled=true`
> then `kc set-password -r pensieve --username … --new-password '…'` (prod policy: 12+ chars, mixed
> case, not username/email).

- [x] Create your account in the `pensieve` realm, set a password, and **flip Email verified → On by
      hand** — `verifyEmail` is off by design, and claim-by-email requires `email_verified: true`
      (an unverified account logs in fine but gets a 403 email-conflict instead of claiming the row).
      Done 2026-08-17 via kcadm (console blocked — see the surprise above).
- [x] `UPDATE users SET email = 'you@domain.com', role_override = 'ADMIN' WHERE is_public_showcase;`
      Done 2026-08-17: `psql` in the `db` container (`-U postgres -d pensieve-db`), `UPDATE 1`.
- [x] Log in once — the first authenticated call stamps `keycloak_sub` on that row. Done 2026-08-17:
      `linked = t` in the users table, `/api/auth/session` reported role ADMIN (which also closed the
      token `aud`/`iss` verification item above).
- [x] **Harden — done 2026-08-18, exactly as originally written, after a detour.** A permanent,
      personally-named admin was created in the master realm (realm role `admin`, password set
      non-temporary, OTP forced via the `CONFIGURE_TOTP` required action and configured at first
      login, full console access verified), and then BOTH bootstrap-created accounts were deleted:
      the original first-boot `admin` and the `tmpadmin` recovery account. `KC_ADMIN_USER` /
      `KC_ADMIN_PASSWORD` are blanked in `.env`. The master realm now holds exactly one user.
      Two operational notes from the detour, for the next person: Keycloak marks every
      bootstrap-created admin as a **temporary account** — it shows a banner, greys out the
      username field, and wants replacing, so "rename the bootstrap admin" is not a supported
      shortcut; and if all admin access is ever lost, the recovery recipe is
      `kc.sh bootstrap-admin user` inside the keycloak container (needs `-e TMP_PW=...` and
      `-e KC_HTTP_MANAGEMENT_PORT=9001` on the exec — full write-up in PastIssues). Interim admin work
      continues through `kcadm` in the container.
- [x] **Confirm a real email arrives** — trigger a password reset. First end-to-end proof of the
      SMTP path against real deliverability. Check the spam folder. Done 2026-08-17, after fixing
      the second surprise of the day, below.
- [x] Exercise the app: log in, create a record, log out, log back in, confirm persistence and that
      you see only your own data. Done 2026-08-17.

> **Surprise #2, 2026-08-17 — the realm baked broken SMTP values.** The password-reset email never
> arrived (Keycloak's "email sent" page shows success regardless — anti-enumeration). Root cause:
> the production `.env` was transcribed from the 2026-08-14 password-manager draft, which carried
> **`SMTP_STARTTLS=true` / `SMTP_SSL=false`** (the 587-style pair, wrong for 2465 — dies at the TLS
> handshake) and the **placeholder from-domain `no-reply@pensieve.example.com`** (unverified with
> Resend). The realm import baked those values. Two lessons for the record:
>
> 1. **`kc get realms/pensieve --fields smtpServer` returning `{ }` does NOT mean "no SMTP config"**
>    — Keycloak masks the block on read to protect the password. Diagnose sends via the keycloak
>    container log (`grep -iE 'mail|smtp'`) and the realm events (`SEND_RESET_PASSWORD` vs
>    `SEND_RESET_PASSWORD_ERROR`), never by reading the realm back.
> 2. **The recovery for a badly-baked SMTP block is a live realm edit**, exactly as the one-way-door
>    note promises: `kc update realms/pensieve -s 'smtpServer={...}'` with the full object (all
>    values as strings, `"starttls": "false"`, `"ssl": "true"`, the real from-domain and API key).
>    Persists in `keycloak-db`; verified fixed 2026-08-17 — reset email delivered to the inbox.
>
> The Droplet `.env`, the password-manager copy, and `.env.production.example` (from-address now
> prefilled with the real value instead of a placeholder) were all corrected the same day.

**Docs, same day, from the transcript:** write the "first bringup" procedure that actually worked
into this file as a new section; correct the bootstrap section in `DevDocumentation.md` if it behaved
differently than written; record every surprise in `PastIssues.md`.

---

## ✅ Backups and monitoring — immediately after first deploy

Before there is data worth losing, not after the deploy script is proven. Built 2026-08-17; the
single remaining follow-up is confirming the first *scheduled* backup run the morning of
2026-08-18 (see the first bullet).

- [x] **`pg_dump` cron for BOTH databases** to off-box storage (DO Spaces, ~$5/mo). `keycloak-db` is
      the one people forget, and it is the irreplaceable one. Done 2026-08-17 — see "The backup
      system" below for the script, the cron entry, and the Spaces setup. First manual run verified
      end to end (both dumps landed in Spaces); **first *scheduled* run still to be confirmed the
      morning of 2026-08-18** (`/var/log/pensieve-backup.log` empty + the day's pair in Spaces).
- [x] **DO snapshots** as well — snapshots solve "the box died"; the dump cron solves "I need
      Tuesday's data." **Decision 2026-08-17: on-demand snapshots, not automated weekly Backups.**
      Take one manually (dashboard → Droplet → Snapshots) before anything risky — a deploy of a new
      version, an OS upgrade, host-level config changes. The daily dump cron is the data-safety
      layer; a snapshot only buys rebuild *speed*, and without one a dead box means re-walking the
      provisioning section of this file from the top (hours, not minutes). Revisit automated weekly
      Backups (~20% of Droplet cost) if that trade stops feeling right. A running-box snapshot is
      only crash-consistent — that is why the dump cron exists alongside it.
- [x] **Rehearse a restore, and write the restore runbook here *while restoring*** — including the
      two-database consistency question (app rows reference Keycloak identities by `keycloak_sub`;
      restoring one without the other orphans users). An untested backup is a hope. Done 2026-08-17
      on the workstation against the day's real dumps — runbook below, written during the restore.
      The rehearsal surfaced one real gotcha (cluster-level roles are not in the dumps) now baked
      into the runbook.
- [x] External uptime monitoring on `https://pensieve.sethcondie.com/api/heartbeat`, the MCP
      `/healthz`, and TLS certificate expiry. Disk-space monitoring on the box. Done 2026-08-17:
      - **UptimeRobot** (free tier), two monitors, both green: a **keyword** monitor on
        `/api/heartbeat` alerting when `online` is *missing* (catches an error page served with a
        200, which a plain status check calls healthy), and a plain HTTP monitor on the MCP
        `/healthz`. 5-minute interval. Alerts go to a real inbox, deliberately not an
        `@pensieve.sethcondie.com` address — a down box can't tell you it's down.
      - **TLS expiry**: no separate paid check. Caddy auto-renews ~30 days out, and the heartbeat
        monitor goes red the moment TLS actually breaks; that's the accepted coverage.
      - **Disk**: DO metrics agent (`do-agent`) installed on the Droplet 2026-08-17 (the
        `curl -sSL https://repos.insights.digitalocean.com/install.sh | bash` installer; verified
        `active` + `enabled`), with a dashboard alert policy on Disk Utilization — threshold 80%
        for 5 minutes → email. Disk was at 10% on install day.
- [x] Record the debugging path: nothing publishes an internet-facing port except Caddy, so shells
      go through `docker compose exec <service> …`; the backend's internal heartbeat is reachable
      with `docker compose exec backend curl localhost:8080/v1/heartbeat`. (The app database is the
      loopback-only exception — `psql` works from the Droplet at `127.0.0.1:5432`, and from a
      workstation through an SSH tunnel; see the IDE section below.)

### The backup system (installed 2026-08-17)

**Off-box storage.** DO Spaces bucket `pensieve-backup` (private, region `sfo3` — deliberately
noted: that is cross-region from the `nyc1` Droplet, so the off-box copies do not share the
Droplet's failure domain), reached from the
Droplet via `rclone` with a remote named `spaces` (`provider=DigitalOcean`,
`endpoint=sfo3.digitaloceanspaces.com`, `acl=private`; the key pair lives in the password manager
and in `/root/.config/rclone/rclone.conf`, mode 600). The Spaces key may be bucket-scoped — a scoped
key gets `AccessDenied` on `rclone lsd spaces:` (list-all-buckets) while working fine inside the
bucket, so test with `rclone lsd spaces:pensieve-backup`, not against the bare remote. A scoped key
is preferred: this credential lives on the production box.

**The script** is `/usr/local/bin/pensieve-backup.sh` (mode 700) — deliberately *outside*
`/opt/pensieve`, so tag checkouts during deploys never interact with it. Reproduced here in full
because the Droplet copy is the only copy:

```bash
#!/usr/bin/env bash
set -euo pipefail

STAMP=$(date +%F_%H%M)
DIR=/opt/backups
mkdir -p "$DIR"

# Both dumps back-to-back so the keycloak_sub cross-reference stays as
# consistent as two separate databases can be. Custom format (-Fc) is
# compressed and is what pg_restore expects.
docker exec pensieve-db-1          pg_dump -U postgres -d pensieve-db -Fc > "$DIR/pensieve-db_$STAMP.dump"
docker exec pensieve-keycloak-db-1 pg_dump -U keycloak -d keycloak    -Fc > "$DIR/keycloak-db_$STAMP.dump"

# Off-box copy, then prune: 7 days local, 30 days in Spaces.
rclone copy "$DIR" spaces:pensieve-backup/pg-dumps
find "$DIR" -name '*.dump' -mtime +7 -delete
rclone delete --min-age 30d spaces:pensieve-backup/pg-dumps
```

Design notes: `docker exec` with the pinned container names (not `docker compose exec`) so cron
needs no compose parsing and no `.env`; the dumps run over the containers' unix sockets, so **no
database password appears anywhere in the backup path**; `set -euo pipefail` means a failed dump
aborts the run *before* the prune lines — old backups are never deleted on a day no new one was
made. Local dumps live in `/opt/backups` (mode 700 — they contain user emails).

**The cron entry** (root's crontab): `15 3 * * * /usr/local/bin/pensieve-backup.sh >> /var/log/pensieve-backup.log 2>&1`
— daily 03:15 UTC. A healthy run prints nothing, so **an empty (or absent) log is the good
outcome**; anything in it is an error. Retention: one pair per day, 7 days on the box, 30 days in
Spaces.

### Restore runbook (rehearsed 2026-08-17 against that day's real dumps)

The unit of restore is the **timestamped pair** — always both dumps from the same run. The app db's
`users.keycloak_sub` is a reference into keycloak-db; a keycloak-db older than the app db is the
dangerous direction (app rows pointing at identities that don't exist yet = orphaned users, the
one-way-door failure). Restoring the pair makes the consistency question moot.

1. **Get a pair.** From Spaces (`rclone copy spaces:pensieve-backup/pg-dumps/<name>.dump .`), the
   Droplet (`scp 'root@<droplet-ip>:/opt/backups/*.dump' .`), or the DO dashboard.
2. **Create the cluster-level roles FIRST.** This is the gotcha the rehearsal found: `pg_dump` is
   per-database, but roles are cluster-level and are **not in the dump**. Restoring without them
   spews ~122 `role "app_rls" does not exist` / `role "keycloak" does not exist` errors from the
   GRANT and ALTER OWNER statements — the *data* still lands, but the app db's RLS grants are
   silently missing, which breaks row-level security when the backend connects. On a fresh cluster:

   ```sql
   CREATE ROLE app_rls NOLOGIN NOSUPERUSER NOINHERIT NOBYPASSRLS;  -- matches V1_14
   CREATE ROLE keycloak LOGIN PASSWORD '<KC_DB_PASSWORD>';
   ```

   In the production topology the roles already exist per container (Flyway's V1_14 made `app_rls`
   in `db`; the image's `POSTGRES_USER` made `keycloak` in `keycloak-db`) — this step matters when
   restoring into any *fresh* Postgres: a rebuilt volume, a rehearsal container, a new host.
3. **Restore** (rehearsal form, into a scratch `postgres:16.15-alpine` container; on a real rebuild
   the same two `pg_restore` lines run in the respective db containers):

   ```bash
   docker run -d --name restore-test -e POSTGRES_PASSWORD=test postgres:16.15-alpine
   docker cp pensieve-db_<stamp>.dump  restore-test:/tmp/
   docker cp keycloak-db_<stamp>.dump  restore-test:/tmp/
   docker exec restore-test psql -U postgres -c "CREATE ROLE app_rls NOLOGIN NOSUPERUSER NOINHERIT NOBYPASSRLS;" -c "CREATE ROLE keycloak LOGIN PASSWORD 'test';"
   docker exec restore-test createdb -U postgres pensieve-db
   docker exec restore-test createdb -U postgres -O keycloak keycloak
   docker exec restore-test pg_restore -U postgres -d pensieve-db /tmp/pensieve-db_<stamp>.dump
   docker exec restore-test pg_restore -U postgres -d keycloak   /tmp/keycloak-db_<stamp>.dump
   ```

   With the roles in place first, both restores complete with **zero** errors (verified).
4. **Verify — data, then linkage.** A successful command is not a successful restore:

   ```bash
   # app db: the users table and its keycloak linkage
   docker exec restore-test psql -U postgres -d pensieve-db -c \
     "SELECT count(*) AS total, count(*) FILTER (WHERE keycloak_sub IS NOT NULL) AS linked,
             count(*) FILTER (WHERE is_public_showcase) AS showcase FROM users;"
   # keycloak db: the accounts
   docker exec restore-test psql -U postgres -d keycloak -c \
     "SELECT username, email, email_verified, enabled FROM user_entity
      WHERE realm_id = (SELECT id FROM realm WHERE name='pensieve');"
   # the cross-database consistency proof: these two values must be EQUAL
   docker exec restore-test psql -U postgres -d pensieve-db -c "SELECT keycloak_sub FROM users;"
   docker exec restore-test psql -U postgres -d keycloak    -c "SELECT id FROM user_entity WHERE username='<user>';"
   ```

   Rehearsal result 2026-08-17: 1 user, linked, showcase; bootstrap account present and verified;
   `keycloak_sub` == `user_entity.id` exactly. Then `docker rm -f restore-test`.

Re-rehearse this runbook occasionally (and after any schema change that touches roles or RLS) —
it is only as good as the last time it was true.

---

## Connecting an IDE to the production database

The app database publishes **`127.0.0.1:5432` on the Droplet only** (added to
`compose.production.yaml` 2026-08-17). A loopback binding is invisible to the internet no matter
what the firewall says — the only way in is to already have a shell on the box, which is exactly
what an SSH tunnel is. Nothing to open in the DO cloud firewall.

IntelliJ setup (Database tool window → `+` → Data Source → PostgreSQL):

1. **SSH/SSL tab** → check *Use SSH tunnel* → create an SSH configuration: the Droplet's IP,
   port 22, your SSH user, *Key pair* auth pointing at the same private key `ssh` uses.
2. **General tab** — these values are from the *Droplet's* perspective, because traffic exits the
   tunnel there:
   - Host `127.0.0.1`, port `5432`
   - User `postgres`, password = `POSTGRES_PASSWORD` from `/opt/pensieve/dockerCompose/.env`
   - Database `pensieve-db`
3. **Set the data source to Read-only** (Options tab). This is production; the IDE will otherwise
   happily run an UPDATE with no WHERE clause.

The same recipe works for plain `psql` (`ssh -N -L 5432:127.0.0.1:5432 <droplet>` then connect to
localhost) and, if ever needed, for `keycloak-db` — publish it on a *different* loopback port
(e.g. `127.0.0.1:5433:5432`) rather than colliding with the app db. It is deliberately not
published today.

---

## ✅ Prove the deploy script — done 2026-08-17

- [x] `DRY_RUN=yes make deploy VERSION=1.0.0` against the live host — all four (now five) preflights
      passed and the remote half rehearsed all nine steps read-only, correctly naming the running
      stack and the rollback target. Nothing changed.
- [x] Fold in anything from the first-deploy transcript the script should do and doesn't — the
      transcript was just `git fetch --tags && git checkout v1.0.0` + `up -d`; the script does both
      and adds everything the hand deploy lacked. The *rollback test* (below) found two real gaps
      instead, both folded in the same day.
- [x] Cut `1.0.1` through `release.sh` (not trivial after all — it carries the Caddyfile
      admin-console fix, the loopback db port, and the SMTP 2465 change) and deployed it for real:
      **1m54s** end to end, backups → pull → switch → health green → containers verified `:1.0.1`.
      The framing headers were confirmed from outside: auth host now serves only Keycloak's
      `frame-ancestors 'self'`; app and MCP hosts kept `'none'` + `DENY`.
- [x] **Rollback tested deliberately: `1.0.1` → `1.0.0` → confirm → `1.0.1` (1m02s).** It earned
      its keep — two findings:
      1. **`v1.0.0` predates the deploy scripts**, so the bootstrap's `git checkout v1.0.0` deleted
         `deploy-production-remote.sh` before exec'ing it — failing mid-handoff and leaving the
         checkout moved (old Caddyfile on disk) under a still-running 1.0.1 stack. The rollback was
         completed by copying the current remote script into the v1.0.0 checkout's `scripts/` and
         running it there (it must sit at `/opt/pensieve/scripts/` — it resolves the repo root from
         its own path). Fix: a new preflight in `deploy-production.sh` refuses, in ~1s and with the
         reason named, any tag not containing the remote script. `v1.0.0` is hand-deploy-only,
         permanently; every tag from `1.0.1` on carries the scripts.
      2. **A rolled-back Caddyfile never took effect**: compose does not recreate a container
         because a bind-mounted file's content changed, so caddy kept serving the 1.0.1 config from
         memory (confirmed by headers) while v1.0.0's Caddyfile sat on disk. Benign in that
         direction — but forward-deploying a Caddyfile-only change would silently not land. First
         fix (rode 1.0.2): a graceful `caddy reload` after `up -d` — **which proved insufficient
         the very next day**: a single-file bind mount pins the file's *inode* at container start,
         and git checkout replaces the file, so the reload re-read the stale copy and exited 0
         while the edge kept serving the old config (the 1.0.2 deploy, 2026-08-18; both PastIssues
         entries). Final fix: step 6 force-recreates caddy unconditionally — only a recreate
         rebinds the mount. Rides the first tag after 1.0.2.
- [x] Preflight rejection confirmed in seconds: unpublished `1.0.2` refused in ~1s at the tag check
      before any SSH; `latest` refused by name; malformed `1.0` refused by shape. All exit 1.
- [x] Banners removed from `README.md` and `DevDocumentation.md`; `scriptExplainer.md` finalized
      with the real timings above.

Remember: **rollback does not roll back the database.** Migrations stay additive so the previous
image runs against the newer schema. And **rollback to `v1.0.0` specifically is by hand** (finding
1 above): copy the current `scripts/deploy-production-remote.sh` into the checkout and run it from
`/opt/pensieve`.

---

## ⬜ Open the doors

- [x] Register remote MCP hosts by hand (production ships no anonymous DCR); grant `offline_access`
      and **attach `pensieve:read` explicitly** (it is no longer a realm default); decode the
      resulting token to confirm an offline token was actually issued. **Done 2026-08-18** — the
      claude.ai connector, registered exactly per the procedure below; first natural-language
      question against the live collection returned correct data.
- [ ] Onboard real users only once the SMTP path is proven — every account is admin-created and
      self-service reset is the only recovery path. Write the onboarding procedure down as it is
      first performed (create in admin console, set email verified, send `execute-actions-email`).
- [ ] Check production-bound data for pre-2026-07-30 corrupted `baseSetId` rows before they become
      v1's data — re-import cannot repair them.
- [ ] Third-party notices and a privacy / data-handling statement.

### Registering a remote MCP host (performed 2026-08-18 — the claude.ai connector)

One OAuth client per host, created by hand in the admin console (anonymous DCR is refused in prod —
the 403 `Host not trusted` in the verification checklist is that refusal working). The conceptual
background — why `pensieve:read` and `offline_access` must be attached explicitly, and the offline
session bounds — is in `keycloak/README.md` ("Offline tokens for MCP connectors"); this is the
click-path that worked.

1. **Create the client.** Admin console → realm **pensieve** → Clients → Create client:
   - Client ID `claude-ai-connector` (name each client for its host)
   - **Client authentication On** (confidential); **Standard flow only** — Direct access grants
     and everything else unchecked
   - Valid redirect URIs — both, exact: `https://claude.ai/api/mcp/auth_callback` and
     `https://claude.com/api/mcp/auth_callback`. Web origins blank (the token exchange is
     server-to-server).
   - Credentials tab → client secret to the password manager
   - Advanced tab → PKCE Code Challenge Method **S256**
2. **Attach the two scopes** (Client scopes tab → Add client scope) — the step nothing on the
   client hints at: **`pensieve:read` as Default** (carries the `aud` mapper; without it the sidecar
   refuses the token twice over) and **`offline_access` as Default** (the sidecar's metadata never
   asks for it, so Optional would silently never be issued).
3. **Pre-flight in the console** before touching the host: client → Client scopes → **Evaluate**
   sub-tab → pick a user → Generated access token must show
   `aud == https://mcp.pensieve.sethcondie.com/mcp` and both scopes in `scope`.
4. **Configure the host.** claude.ai → Settings → Connectors → Add custom connector: URL
   `https://mcp.pensieve.sethcondie.com/mcp`; Advanced settings → the client ID and secret
   (supplying them makes claude.ai skip DCR). The browser window that opens is Keycloak — log in
   with the **pensieve-realm** user account (not the master-realm admin).
5. **Verify server-side** — the token lives on Anthropic's servers, so the proof an offline token
   was issued is the **Offline session** on Users → \<user\> → Sessions (it can only exist if a
   `typ: Offline` refresh token was minted), plus the functional test: ask a question, get correct
   data, scoped to that user's own collection.

Operating notes: an idle connector expires at 10 days, an active one at 30 (`offlineSessionIdleTimeout`
/ `offlineSessionMaxLifespan`) — periodic "reconnect" prompts in claude.ai are the realm settings
working. Revoke access by deleting the offline session or the consent on the user; no password change
needed. **Claude Code is a different registration problem** (localhost callback, DCR/CIMD, no fixed
redirect URI) — this recipe does not transfer; see the CIMD note in `keycloak/README.md` when that
host is wanted.
