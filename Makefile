# Thin aliases only — all logic lives in scripts/
# Sibling repo locations default to checkouts beside this one; override per invocation if needed:
#   make release VERSION=1.4.0 WEB_REPO=/elsewhere/web MCP_REPO=/elsewhere/mcp
WEB_REPO ?= ../the-game-pensieve-web-v2
MCP_REPO ?= ../the-game-pensieve-mcp

.PHONY: release deploy rehearse

# Stand the production stack up locally (compose.production.yaml + Caddyfile, unmodified, real TLS),
# verify it, tear it down. No VERSION: it rehearses whatever the compose file currently pins.
#   make rehearse                                   generates .env.rehearsal on first run
#   make rehearse ENV_FILE=.env.rehearsal           an env file you filled in yourself
#   SMTP_TEST_TO=you@example.com make rehearse      + the Phase B0 email check
#   KEEP_STACK=1 CREATE_TEST_USER=1 make rehearse   + a user, to do the browser half by hand
ENV_FILE ?= .env.rehearsal

rehearse:
	./scripts/prod-rehearsal.sh "$(ENV_FILE)"

release:
ifndef VERSION
	$(error VERSION is required: make release VERSION=1.4.0)
endif
	./scripts/release.sh "$(VERSION)" "$(WEB_REPO)" "$(MCP_REPO)"

deploy:
ifndef VERSION
	$(error VERSION is required: make deploy VERSION=1.4.0)
endif
	@test -x scripts/deploy-production.sh || { \
	    echo "error: scripts/deploy-production.sh does not exist yet (Pipeline B — documentation/future-plan-implement-pipeline-b.md, phase B3)"; exit 1; }
	./scripts/deploy-production.sh "$(VERSION)"
