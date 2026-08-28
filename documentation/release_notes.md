# Release notes — 1.0.5 released 8-28-2026

- **Typo-domain redirect.** `pensive.sethcondie.com` (the common misspelling, missing the second "e")
  now 301s to the same path on `pensieve.sethcondie.com`, via a redirect-only Caddy site block driven
  by the new optional `APP_ALIAS_DOMAIN`. Set `APP_ALIAS_DOMAIN=pensive.sethcondie.com` in the
  Droplet's `dockerCompose/.env.production` before deploying; the A record is already live.
- Added sorting to the saved filters. At least one filter is still required to store a saved filter.
- Updated the mobile UI headers and buttons to leave more room for the games' information.