import type { FastifyInstance } from "fastify";
import { requireBearerAuth } from "../plugins/auth.js";
import { scheduleStore } from "../store.js";

export async function locationsRoutes(app: FastifyInstance): Promise<void> {
  // Deliberately no ETag here — keeps discovery cheap and decoupled from the
  // per-location ETag scheme, per Features/client-offline-sync.md.
  app.get("/api/locations", { preHandler: requireBearerAuth }, async () => scheduleStore.getManifest());
}
