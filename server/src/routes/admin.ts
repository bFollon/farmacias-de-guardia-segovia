import { createHash } from "node:crypto";
import type { FastifyInstance } from "fastify";
import { requireBearerAuth } from "../plugins/auth.js";
import { isLocationId } from "../types.js";
import { scheduleStore } from "../store.js";
import { publishLocationSchedule } from "../publish.js";
import { REGION_PARSERS } from "../parsers/index.js";
import { REGION_VALIDATION_CONFIG } from "../validation/regionConfig.js";
import { validateSchedules } from "../validation/gate.js";

export async function adminRoutes(app: FastifyInstance): Promise<void> {
  app.post("/api/admin/reload", { preHandler: requireBearerAuth }, async () => {
    const result = await scheduleStore.reload();
    return result;
  });

  app.post<{ Params: { locationId: string } }>(
    "/api/refresh/:locationId",
    { preHandler: requireBearerAuth },
    async (request, reply) => {
      const { locationId } = request.params;

      if (!isLocationId(locationId)) {
        return reply.code(404).send({ error: `Unknown locationId: ${locationId}` });
      }

      // Phase 1, step 2 only ported segovia-capital (Features/parser-port-typescript.md);
      // the rest fall through to this until Phase 2.
      const parser = REGION_PARSERS[locationId];
      if (!parser) {
        return reply.code(501).send({ error: `No parser registered yet for ${locationId}` });
      }

      // No PDFURLScrapingService port yet — sourcePdfUrl is a static fallback, matching
      // PDFURLRepository's fallback-URL role on the clients. See backend-data-service.md.
      const pdfResponse = await fetch(parser.sourcePdfUrl);
      if (!pdfResponse.ok) {
        return reply
          .code(502)
          .send({ error: `Failed to fetch source PDF: ${pdfResponse.status} ${pdfResponse.statusText}` });
      }
      const pdfBytes = new Uint8Array(await pdfResponse.arrayBuffer());
      const sourcePdfSha256 = createHash("sha256").update(pdfBytes).digest("hex");

      const schedules = await parser.parse(pdfBytes, parser.sourcePdfUrl);

      const previous = scheduleStore.get(locationId);
      const validationConfig = REGION_VALIDATION_CONFIG[locationId];
      const validation = validationConfig
        ? validateSchedules(schedules, validationConfig, previous)
        : { passed: true, failures: [] };

      if (!validation.passed) {
        // Fail closed: previous published JSON stays live, per parsing-validation-gate.md.
        return reply.code(422).send({
          error: "Validation failed — keeping previously published data",
          failures: validation.failures,
        });
      }

      if (previous?.sourcePdfSha256 === sourcePdfSha256) {
        return { published: false, reason: "Source PDF unchanged since last publish", version: previous.version };
      }

      const nextVersion = (previous?.version ?? 0) + 1;
      await publishLocationSchedule({
        locationId,
        regionId: parser.regionId,
        sourcePdfUrl: parser.sourcePdfUrl,
        sourcePdfSha256,
        parsedAt: new Date().toISOString(),
        version: nextVersion,
        schedules,
      });

      return { published: true, version: nextVersion, scheduleCount: schedules.length };
    },
  );
}
