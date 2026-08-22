import type { FastifyInstance } from "fastify";
import { requireBearerAuth } from "../plugins/auth.js";
import { isLocationId } from "../types.js";
import { scheduleStore } from "../store.js";

export async function adminRoutes(app: FastifyInstance): Promise<void> {
  app.post("/api/admin/reload", { preHandler: requireBearerAuth }, async () => {
    const result = await scheduleStore.reload();
    return result;
  });

  // Stubbed until Features/parser-port-typescript.md's parsers are ported (Phase 1, step 2) —
  // this service is scaffolded "empty of real parsers initially" per the migration plan.
  app.post<{ Params: { locationId: string } }>(
    "/api/refresh/:locationId",
    { preHandler: requireBearerAuth },
    async (request, reply) => {
      const { locationId } = request.params;

      if (!isLocationId(locationId)) {
        return reply.code(404).send({ error: `Unknown locationId: ${locationId}` });
      }

      return reply.code(501).send({
        error: `No parser registered yet for ${locationId}`,
      });
    },
  );
}
