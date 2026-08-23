import type { FastifyReply, FastifyRequest } from "fastify";
import { config } from "../config.js";

function checkBearer(request: FastifyRequest, expected: string): boolean {
  const header = request.headers.authorization ?? "";
  if (!header.startsWith("Bearer ")) return false;
  return Boolean(expected) && header.slice("Bearer ".length) === expected;
}

/**
 * Prehandler for client-facing reads (GET /api/locations, GET /api/schedules/:id) — this
 * token is safe to embed in the iOS/Android apps. Matches InterSegoService's requireApiKey.
 */
export async function requireApiKey(request: FastifyRequest, reply: FastifyReply): Promise<void> {
  if (!checkBearer(request, config.apiKey)) {
    await reply.code(401).send({ error: "Unauthorized" });
  }
}

/**
 * Prehandler for admin-only operations (POST /api/refresh/:id, POST /api/admin/reload) —
 * a separate key that never leaves the server, so extracting API_KEY from a shipped app
 * doesn't grant the ability to trigger fetches against cofsegovia.com or force a
 * republish. Matches InterSegoService's requireReloadKey.
 */
export async function requireReloadKey(request: FastifyRequest, reply: FastifyReply): Promise<void> {
  if (!checkBearer(request, config.reloadKey)) {
    await reply.code(401).send({ error: "Unauthorized" });
  }
}
