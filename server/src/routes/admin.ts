import { createHash } from "node:crypto";
import type { FastifyInstance, FastifyReply } from "fastify";
import { requireBearerAuth } from "../plugins/auth.js";
import { isLocationId } from "../types.js";
import { scheduleStore } from "../store.js";
import { publishLocationSchedule } from "../publish.js";
import { REGION_PARSERS } from "../parsers/index.js";
import { isRuralZbsId, parseAllRuralZbs, RURAL_SOURCE_PDF_URL, RURAL_ZBS_IDS } from "../parsers/rural.js";
import { REGION_VALIDATION_CONFIG } from "../validation/regionConfig.js";
import { validateSchedules } from "../validation/gate.js";
import type { PharmacySchedule } from "../types.js";

async function fetchPdf(url: string): Promise<{ bytes: Uint8Array; sha256: string } | { error: string }> {
  const response = await fetch(url);
  if (!response.ok) {
    return { error: `Failed to fetch source PDF: ${response.status} ${response.statusText}` };
  }
  const bytes = new Uint8Array(await response.arrayBuffer());
  return { bytes, sha256: createHash("sha256").update(bytes).digest("hex") };
}

async function refreshSingleRegion(locationId: string, reply: FastifyReply) {
  const parser = REGION_PARSERS[locationId];
  if (!parser) {
    return reply.code(501).send({ error: `No parser registered yet for ${locationId}` });
  }

  // No PDFURLScrapingService port yet — sourcePdfUrl is a static fallback, matching
  // PDFURLRepository's fallback-URL role on the clients. See backend-data-service.md.
  const pdf = await fetchPdf(parser.sourcePdfUrl);
  if ("error" in pdf) return reply.code(502).send({ error: pdf.error });

  const schedules = await parser.parse(pdf.bytes, parser.sourcePdfUrl);
  const previous = scheduleStore.get(locationId);
  const validationConfig = REGION_VALIDATION_CONFIG[locationId];
  const validation = validationConfig ? validateSchedules(schedules, validationConfig, previous) : { passed: true, failures: [] };

  if (!validation.passed) {
    return reply.code(422).send({ error: "Validation failed — keeping previously published data", failures: validation.failures });
  }

  if (previous?.sourcePdfSha256 === pdf.sha256) {
    return { published: false, reason: "Source PDF unchanged since last publish", version: previous.version };
  }

  const nextVersion = (previous?.version ?? 0) + 1;
  await publishLocationSchedule({
    locationId,
    regionId: parser.regionId,
    sourcePdfUrl: parser.sourcePdfUrl,
    sourcePdfSha256: pdf.sha256,
    parsedAt: new Date().toISOString(),
    version: nextVersion,
    schedules,
  });

  return { published: true, version: nextVersion, scheduleCount: schedules.length };
}

/**
 * Segovia Rural is one PDF backing all 8 ZBS files (see parsers/rural.ts). Per
 * pdf-change-monitor.md, refreshing any one of the 8 ids re-parses and republishes all 8
 * from that same PDF fetch. Per parsing-validation-gate.md's edge case note, every ZBS
 * must pass validation before ANY of them publish — a partial failure must not silently
 * publish 7 good + 1 stale, so this validates all 8 first and only writes files if all pass.
 */
async function refreshRural(reply: FastifyReply) {
  const pdf = await fetchPdf(RURAL_SOURCE_PDF_URL);
  if ("error" in pdf) return reply.code(502).send({ error: pdf.error });

  const schedulesByZbs = await parseAllRuralZbs(pdf.bytes, RURAL_SOURCE_PDF_URL);

  const unchanged = RURAL_ZBS_IDS.every((id) => scheduleStore.get(id)?.sourcePdfSha256 === pdf.sha256);
  if (unchanged) {
    return { published: false, reason: "Source PDF unchanged since last publish" };
  }

  const failuresByZbs: Record<string, string[]> = {};
  for (const zbsId of RURAL_ZBS_IDS) {
    const schedules: PharmacySchedule[] = schedulesByZbs[zbsId] ?? [];
    const previous = scheduleStore.get(zbsId);
    const validationConfig = REGION_VALIDATION_CONFIG[zbsId];
    const validation = validationConfig ? validateSchedules(schedules, validationConfig, previous) : { passed: true, failures: [] };
    if (!validation.passed) failuresByZbs[zbsId] = validation.failures;
  }

  if (Object.keys(failuresByZbs).length > 0) {
    return reply.code(422).send({
      error: "Validation failed for one or more ZBS — keeping previously published data for all 8",
      failuresByZbs,
    });
  }

  const publishedVersions: Record<string, number> = {};
  for (const zbsId of RURAL_ZBS_IDS) {
    const previous = scheduleStore.get(zbsId);
    const nextVersion = (previous?.version ?? 0) + 1;
    await publishLocationSchedule({
      locationId: zbsId,
      regionId: "segovia-rural",
      sourcePdfUrl: RURAL_SOURCE_PDF_URL,
      sourcePdfSha256: pdf.sha256,
      parsedAt: new Date().toISOString(),
      version: nextVersion,
      schedules: schedulesByZbs[zbsId] ?? [],
    });
    publishedVersions[zbsId] = nextVersion;
  }

  return { published: true, versions: publishedVersions };
}

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

      if (isRuralZbsId(locationId)) return refreshRural(reply);
      return refreshSingleRegion(locationId, reply);
    },
  );
}
