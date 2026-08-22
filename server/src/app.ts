import Fastify, { type FastifyInstance } from "fastify";
import { healthRoutes } from "./routes/health.js";
import { locationsRoutes } from "./routes/locations.js";
import { schedulesRoutes } from "./routes/schedules.js";
import { adminRoutes } from "./routes/admin.js";
import { scheduleStore } from "./store.js";

export async function buildApp(): Promise<FastifyInstance> {
  const app = Fastify({ logger: true });

  await app.register(healthRoutes);
  await app.register(locationsRoutes);
  await app.register(schedulesRoutes);
  await app.register(adminRoutes);

  await scheduleStore.reload();

  return app;
}
