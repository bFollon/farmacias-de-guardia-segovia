import type { FastifyInstance } from "fastify";

export async function healthRoutes(app: FastifyInstance): Promise<void> {
  // Unauthenticated liveness check, per the API summary table in Features/backend-data-service.md.
  app.get("/health", async () => ({ status: "ok" }));
}
