import type { FastifyReply, FastifyRequest } from "fastify";
import { config } from "../config.js";

/**
 * Bearer-auth preHandler shared by every non-/health route. Matches the "shared
 * bearer token, no per-endpoint auth tiers" MVP decision in Features/backend-data-service.md.
 */
export async function requireBearerAuth(request: FastifyRequest, reply: FastifyReply): Promise<void> {
  const header = request.headers.authorization ?? "";
  const token = header.startsWith("Bearer ") ? header.slice("Bearer ".length) : "";

  if (!config.apiToken || token !== config.apiToken) {
    await reply.code(401).send({ error: "Unauthorized" });
  }
}
