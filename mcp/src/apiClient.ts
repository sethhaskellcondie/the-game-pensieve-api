import type { Config } from "./config.js";
import { SEARCH_PATHS, type EntityKey } from "./entities.js";

/** A single filter entry as supplied by the model (the entity `key` is injected by the client). */
export interface FilterEntry {
  field: string;
  operator: string;
  operand: string;
}

export interface Heartbeat {
  message: string;
  secureMode: boolean;
}

/** Thrown for any non-2xx response or transport failure from the backend. */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly path: string,
    public readonly details: unknown,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/** The read-only slice of the backend the sidecar consumes. */
export interface PensieveApi {
  heartbeat(): Promise<Heartbeat>;
  getAvailableFilters(entityKey: EntityKey): Promise<unknown>;
  search(entityKey: EntityKey, filters: FilterEntry[]): Promise<unknown[]>;
  getCustomFields(entityKey?: EntityKey): Promise<unknown[]>;
  listShowcases(): Promise<unknown[]>;
}

export function createApiClient(cfg: Config): PensieveApi {
  async function request<T>(method: "GET" | "POST", path: string, body?: unknown): Promise<T> {
    const url = `${cfg.apiBaseUrl}${path}`;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), cfg.requestTimeoutMs);

    let res: Response;
    try {
      res = await fetch(url, {
        method,
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: controller.signal,
      });
    } catch (err) {
      const reason = controller.signal.aborted ? `timed out after ${cfg.requestTimeoutMs}ms` : String(err);
      throw new ApiError(0, path, reason, `Request to ${method} ${path} failed: ${reason}`);
    } finally {
      clearTimeout(timer);
    }

    const text = await res.text();
    let parsed: unknown = null;
    if (text.length > 0) {
      try {
        parsed = JSON.parse(text);
      } catch {
        throw new ApiError(res.status, path, text, `Non-JSON response (${res.status}) from ${path}`);
      }
    }

    if (!res.ok) {
      const envelope = parsed as { errors?: unknown } | null;
      const details = envelope?.errors ?? parsed ?? text;
      throw new ApiError(res.status, path, details, `Backend returned ${res.status} for ${path}`);
    }

    // Unwrap the { data, errors, roundTripMs } envelope.
    return (parsed as { data?: T } | null)?.data as T;
  }

  return {
    heartbeat: () => request<Heartbeat>("GET", "/heartbeat"),

    getAvailableFilters: (entityKey) => request<unknown>("GET", `/filters/${encodeURIComponent(entityKey)}`),

    search: (entityKey, filters) =>
      request<unknown[]>("POST", `/${SEARCH_PATHS[entityKey]}/function/search`, {
        filters: filters.map((f) => ({
          key: entityKey,
          field: f.field,
          operator: f.operator,
          operand: f.operand,
        })),
      }),

    getCustomFields: (entityKey) =>
      entityKey === undefined
        ? request<unknown[]>("GET", "/custom_fields")
        : request<unknown[]>("GET", `/custom_fields/entity/${encodeURIComponent(entityKey)}`),

    listShowcases: () => request<unknown[]>("GET", "/showcases"),
  };
}
