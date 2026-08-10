# Thin aliases only — all logic lives in scripts/ (pipeline notes §3, localFiles/pipeline_notes.md).
# Sibling repo locations default to checkouts beside this one; override per invocation if needed:
#   make release VERSION=1.4.0 WEB_REPO=/elsewhere/web MCP_REPO=/elsewhere/mcp
WEB_REPO ?= ../the-game-pensieve-web-v2
MCP_REPO ?= ../the-game-pensieve-mcp

.PHONY: release deploy

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
	    echo "error: scripts/deploy-production.sh does not exist yet (Pipeline B — localFiles/pipeline_notes.md, section 6)"; exit 1; }
	./scripts/deploy-production.sh "$(VERSION)"
