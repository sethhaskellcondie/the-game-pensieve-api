import { loadConfig } from "./config.js";
import { ApiError, createApiClient } from "./apiClient.js";
import { createHttpApp } from "./httpApp.js";

async function main(): Promise<void> {
  const cfg = loadConfig();
  const api = createApiClient(cfg);

  // Phase 1 mode detection: probe the backend and report whether it is in secure mode.
  try {
    const hb = await api.heartbeat();
    console.log(
      `[pensieve-mcp] connected to API at ${cfg.apiBaseUrl} — secureMode=${hb.secureMode} ("${hb.message}")`,
    );
    if (hb.secureMode) {
      console.warn(
        "[pensieve-mcp] WARNING: backend reports secureMode=true, but this sidecar does not yet enforce " +
          "OAuth (Phase 3+). Requests are sent unauthenticated and may be rejected or limited to public data.",
      );
    }
  } catch (err) {
    const reason = err instanceof ApiError ? err.message : String(err);
    console.warn(
      `[pensieve-mcp] WARNING: could not reach the API heartbeat at ${cfg.apiBaseUrl}: ${reason}. ` +
        "Starting anyway; tool calls will fail until the backend is reachable.",
    );
  }

  const app = createHttpApp(api, { name: cfg.serverName, version: cfg.serverVersion });
  app.listen(cfg.port, () => {
    console.log(`[pensieve-mcp] Streamable HTTP MCP server listening on :${cfg.port} (POST /mcp)`);
  });
}

main().catch((err) => {
  console.error("[pensieve-mcp] fatal:", err);
  process.exit(1);
});
