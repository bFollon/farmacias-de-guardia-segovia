export const config = {
  port: Number(process.env.PORT ?? 3000),
  host: process.env.HOST ?? "0.0.0.0",
  /** Client-facing bearer token — gates GET /api/locations and GET /api/schedules/:id.
   * Safe to embed in app binaries, matching InterSegoService's API_KEY. */
  apiKey: process.env.API_KEY ?? "",
  /** Admin-only bearer token — gates POST /api/refresh/:id and POST /api/admin/reload.
   * Never bundled into app binaries: even if API_KEY is extracted from a shipped app,
   * that shouldn't grant the ability to trigger fetches against cofsegovia.com or force
   * a republish. Matches InterSegoService's RELOAD_KEY. */
  reloadKey: process.env.RELOAD_KEY ?? "",
  dataDir: process.env.DATA_DIR ?? new URL("../data/schedules", import.meta.url).pathname,
};

if (process.env.NODE_ENV === "production") {
  if (!config.apiKey) throw new Error("API_KEY must be set in production");
  if (!config.reloadKey) throw new Error("RELOAD_KEY must be set in production");
}
