export interface Config {
  /** Backend REST API base URL including the /v1 prefix, no trailing slash. */
  apiBaseUrl: string;
  /** Port the Streamable HTTP MCP server listens on. */
  port: number;
  /** Per-request timeout to the backend, in milliseconds. */
  requestTimeoutMs: number;
  /** MCP server identity advertised on the initialize handshake. */
  serverName: string;
  serverVersion: string;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const apiBaseUrl = (env.API_BASE_URL ?? "http://localhost:8080/v1").replace(/\/+$/, "");
  const port = Number.parseInt(env.PORT ?? "3000", 10);
  const requestTimeoutMs = Number.parseInt(env.API_TIMEOUT_MS ?? "15000", 10);

  if (!Number.isFinite(port) || port <= 0) {
    throw new Error(`Invalid PORT: ${env.PORT}`);
  }
  if (!Number.isFinite(requestTimeoutMs) || requestTimeoutMs <= 0) {
    throw new Error(`Invalid API_TIMEOUT_MS: ${env.API_TIMEOUT_MS}`);
  }

  return {
    apiBaseUrl,
    port,
    requestTimeoutMs,
    serverName: "game-pensieve",
    serverVersion: "0.1.0",
  };
}
