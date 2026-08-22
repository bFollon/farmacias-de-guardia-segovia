import type { FastifyInstance } from "fastify";
import { requireBearerAuth } from "../plugins/auth.js";
import { isLocationId } from "../types.js";
import { scheduleStore } from "../store.js";

export async function schedulesRoutes(app: FastifyInstance): Promise<void> {
  app.get<{ Params: { locationId: string } }>(
    "/api/schedules/:locationId",
    { preHandler: requireBearerAuth },
    async (request, reply) => {
      const { locationId } = request.params;

      if (!isLocationId(locationId)) {
        return reply.code(404).send({ error: `Unknown locationId: ${locationId}` });
      }

      const location = scheduleStore.get(locationId);
      if (!location) {
        return reply.code(404).send({ error: `No schedule data for ${locationId} yet` });
      }

      const etag = scheduleStore.etagFor(locationId);
      if (etag && request.headers["if-none-match"] === etag) {
        return reply.code(304).send();
      }

      if (etag) reply.header("ETag", etag);
      return location;
    },
  );
}
