// Loads .env into process.env before anything else runs — matches InterSego's pattern
// (InterSegoService/src/index.ts, InterSegoMonitor/src/index.ts) so `.env` works the same
// way under pm2 here as it does for the other services on this Pi. Must be the first
// import: config.ts reads process.env at module-load time.
import "dotenv/config";
import { buildApp } from "./app.js";
import { config } from "./config.js";

const app = await buildApp();

try {
  await app.listen({ port: config.port, host: config.host });
} catch (err) {
  app.log.error(err);
  process.exit(1);
}
