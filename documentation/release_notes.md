# Release notes — 1.0.5 unreleased

- **Typo-domain redirect.** `pensive.sethcondie.com` (the common misspelling, missing the second "e")
  now 301s to the same path on `pensieve.sethcondie.com`, via a redirect-only Caddy site block driven
  by the new optional `APP_ALIAS_DOMAIN`. Set `APP_ALIAS_DOMAIN=pensive.sethcondie.com` in the
  Droplet's `dockerCompose/.env.production` before deploying; the A record is already live.
