import express, { type Express, type Request, type Response } from "express";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import type { PensieveApi } from "./apiClient.js";
import { createServer } from "./server.js";

function methodNotAllowed(_req: Request, res: Response): void {
  res
    .status(405)
    .set("Allow", "POST")
    .json({
      jsonrpc: "2.0",
      error: { code: -32000, message: "Method not allowed. This stateless MCP endpoint only accepts POST." },
      id: null,
    });
}

/**
 * Express app exposing the MCP server over Streamable HTTP at POST /mcp (stateless:
 * a fresh server + transport per request), plus a /healthz liveness probe.
 */
export function createHttpApp(api: PensieveApi, opts: { name: string; version: string }): Express {
  const app = express();
  app.use(express.json());

  app.get("/healthz", (_req, res) => {
    res.json({ status: "ok" });
  });

  app.post("/mcp", async (req, res) => {
    const server = createServer(api, opts);
    const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });
    res.on("close", () => {
      void transport.close();
      void server.close();
    });
    try {
      await server.connect(transport);
      await transport.handleRequest(req, res, req.body);
    } catch (err) {
      console.error("[pensieve-mcp] error handling /mcp request:", err);
      if (!res.headersSent) {
        res.status(500).json({
          jsonrpc: "2.0",
          error: { code: -32603, message: "Internal server error" },
          id: null,
        });
      }
    }
  });

  // Streamable HTTP GET (server-initiated SSE) and DELETE (session teardown) require
  // stateful sessions, which this stateless server does not use.
  app.get("/mcp", methodNotAllowed);
  app.delete("/mcp", methodNotAllowed);

  return app;
}
