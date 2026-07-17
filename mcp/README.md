# The Game Pensieve — MCP Sidecar

A read-only **MCP (Model Context Protocol)** server for The Game Pensieve API. It is a **sidecar
proxy**: a separate TypeScript process that exposes MCP tools over **Streamable HTTP** and fulfills
them by calling the existing REST API over HTTP. AI hosts (Claude Desktop, Claude Code, claude.ai
connectors) connect to it to answer natural-language questions about a game collection.

Status: **Phases 1–2** of `../localFiles/mcp_rollout.md` — scaffold + transport, and the read-only
tool surface against a local (permit-all) instance. Secure-mode OAuth (Phases 3–5) is not built yet;
in secure mode the sidecar currently sends unauthenticated requests and logs a warning.

## Tools

| Tool | REST endpoint | Purpose |
|---|---|---|
| `get_available_filters(entityKey)` | `GET /v1/filters/{key}` | Valid fields + operators for an entity |
| `search_systems` / `search_toys` / `search_video_games` / `search_video_game_boxes` / `search_board_games` / `search_board_game_boxes` `(filters?)` | `POST /v1/{entity}/function/search` | Filtered search; the entity `key` is injected automatically |
| `get_custom_fields(entityKey?)` | `GET /v1/custom_fields[/entity/{key}]` | Custom field definitions |
| `get_collection_summary()` | six searches | Item counts per entity + total |
| `list_showcases()` | `GET /v1/showcases` | Public showcases (slug + name) |

All tools are read-only. Filters are `{ field, operator, operand }` objects; call
`get_available_filters` first to learn the valid combinations. An empty/omitted filter list returns
everything.

## Configuration

| Env var | Default | Meaning |
|---|---|---|
| `API_BASE_URL` | `http://localhost:8080/v1` | Backend base URL incl. `/v1` (compose: `http://backend:8080/v1`) |
| `PORT` | `3000` | Port the `/mcp` endpoint listens on |
| `API_TIMEOUT_MS` | `15000` | Per-request timeout to the backend |

## Develop

```bash
npm install
npm run dev        # tsx watch, reads .env-style vars from the environment
npm test           # vitest (hermetic; no backend needed)
npm run typecheck
npm run build && npm start
```

On startup the server probes `GET /v1/heartbeat` and logs the backend's `secureMode`.

## Try it against a local backend

Bring up the API in permit-all mode (default `local`/`docker` profile), then:

```bash
API_BASE_URL=http://localhost:8080/v1 PORT=8090 npm start
npx @modelcontextprotocol/inspector      # point it at http://localhost:8090/mcp
# or register with a host:
claude mcp add --transport http pensieve http://localhost:8090/mcp
```

## Docker / Compose

Built as its own service (`mcp`) in `../compose.yaml`, on the backend's network:

```bash
docker compose up -d backend mcp     # from the repo root
# MCP endpoint: http://localhost:8090/mcp
```

## Transport notes

Streamable HTTP, **stateless**: each `POST /mcp` spins up a fresh server + transport. `GET`/`DELETE`
on `/mcp` return `405` (no server-initiated streams or sessions). `GET /healthz` is a liveness probe.
