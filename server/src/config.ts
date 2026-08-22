export const config = {
  port: Number(process.env.PORT ?? 3000),
  host: process.env.HOST ?? "0.0.0.0",
  /** Shared bearer token gating every non-/health endpoint (see Features/backend-data-service.md). */
  apiToken: process.env.API_TOKEN ?? "",
  dataDir: process.env.DATA_DIR ?? new URL("../data/schedules", import.meta.url).pathname,
};

if (!config.apiToken && process.env.NODE_ENV === "production") {
  throw new Error("API_TOKEN must be set in production");
}
