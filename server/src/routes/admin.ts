import { createHash } from "node:crypto";
import type { FastifyInstance, FastifyReply } from "fastify";
import { requireReloadKey } from "../plugins/auth.js";
import { isLocationId } from "../types.js";
import { scheduleStore } from "../store.js";
import { publishLocationSchedule } from "../publish.js";
import { REGION_PARSERS } from "../parsers/index.js";
import { isRuralZbsId, parseAllRuralZbs, RURAL_SOURCE_PDF_URL, RURAL_ZBS_IDS } from "../parsers/rural.js";
import { resolvePdfUrl, type RegionId } from "../scraping/pdfUrlScraper.js";
import { REGION_VALIDATION_CONFIG } from "../validation/regionConfig.js";
import { validateSchedules } from "../validation/gate.js";
import type { LocationSchedule, PharmacySchedule } from "../types.js";

interface PdfHeadInfo {
  lastModified?: string;
  etag?: string;
}

/** Cheap change check: a HEAD request costs nothing next to downloading a ~1MB PDF, and
 * cofsegovia.com sends both Last-Modified and ETag, so most refresh calls can skip the
 * download entirely. Returns undefined if the request fails or the source sends neither
 * header — callers should fall back to a full fetch when this happens. */
async function headPdf(url: string): Promise<PdfHeadInfo | undefined> {
  try {
    const response = await fetch(url, { method: "HEAD" });
    if (!response.ok) return undefined;
    const lastModified = response.headers.get("last-modified") ?? undefined;
    const etag = response.headers.get("etag") ?? undefined;
    if (!lastModified && !etag) return undefined;
    return { lastModified, etag };
  } catch {
    return undefined;
  }
}

function pdfUnchanged(head: PdfHeadInfo | undefined, previous: LocationSchedule | undefined): boolean {
  if (!head || !previous) return false;
  if (head.etag && head.etag === previous.sourcePdfEtag) return true;
  if (head.lastModified && head.lastModified === previous.sourcePdfLastModified) return true;
  return false;
}

async function fetchPdf(
  url: string,
): Promise<{ bytes: Uint8Array; sha256: string; lastModified?: string; etag?: string } | { error: string }> {
  const response = await fetch(url);
  if (!response.ok) {
    return { error: `Failed to fetch source PDF: ${response.status} ${response.statusText}` };
  }
  const bytes = new Uint8Array(await response.arrayBuffer());
  return {
    bytes,
    sha256: createHash("sha256").update(bytes).digest("hex"),
    lastModified: response.headers.get("last-modified") ?? undefined,
    etag: response.headers.get("etag") ?? undefined,
  };
}

async function refreshSingleRegion(locationId: string, reply: FastifyReply) {
  const parser = REGION_PARSERS[locationId];
  if (!parser) {
    return reply.code(501).send({ error: `No parser registered yet for ${locationId}` });
  }

  const previous = scheduleStore.get(locationId);

  // Scrapes cofsegovia.com's listing page for the current URL first, falling back to
  // parser.sourcePdfUrl (a static fallback, matching PDFURLRepository's fallback-URL role
  // on the clients) if scraping fails or doesn't cover this region.
  const sourcePdfUrl = await resolvePdfUrl(parser.regionId as RegionId, parser.sourcePdfUrl);

  const head = await headPdf(sourcePdfUrl);
  if (pdfUnchanged(head, previous)) {
    return { published: false, reason: "Source PDF unchanged (HEAD check, no download needed)", version: previous!.version };
  }

  const pdf = await fetchPdf(sourcePdfUrl);
  if ("error" in pdf) return reply.code(502).send({ error: pdf.error });

  if (previous?.sourcePdfSha256 === pdf.sha256) {
    return { published: false, reason: "Source PDF unchanged since last publish", version: previous.version };
  }

  const schedules = await parser.parse(pdf.bytes, sourcePdfUrl);
  const validationConfig = REGION_VALIDATION_CONFIG[locationId];
  const validation = validationConfig ? validateSchedules(schedules, validationConfig, previous) : { passed: true, failures: [] };

  if (!validation.passed) {
    return reply.code(422).send({ error: "Validation failed — keeping previously published data", failures: validation.failures });
  }

  const nextVersion = (previous?.version ?? 0) + 1;
  await publishLocationSchedule({
    locationId,
    regionId: parser.regionId,
    sourcePdfUrl,
    sourcePdfSha256: pdf.sha256,
    sourcePdfLastModified: pdf.lastModified,
    sourcePdfEtag: pdf.etag,
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
  // All 8 ZBS share one source PDF, so one HEAD check (against any one ZBS's stored
  // headers — they're always published together) covers all of them.
  const previous = scheduleStore.get(RURAL_ZBS_IDS[0]);

  // Scrapes cofsegovia.com's listing page for the current URL first, falling back to
  // RURAL_SOURCE_PDF_URL if scraping fails or doesn't cover this region. Segovia Rural's
  // filename encodes year/month, so it rotates monthly on cofsegovia.com — the static
  // fallback alone would 404 as soon as the old month's file is removed.
  const sourcePdfUrl = await resolvePdfUrl("segovia-rural", RURAL_SOURCE_PDF_URL);

  const head = await headPdf(sourcePdfUrl);
  if (pdfUnchanged(head, previous)) {
    return { published: false, reason: "Source PDF unchanged (HEAD check, no download needed)" };
  }

  const pdf = await fetchPdf(sourcePdfUrl);
  if ("error" in pdf) return reply.code(502).send({ error: pdf.error });

  if (previous?.sourcePdfSha256 === pdf.sha256) {
    return { published: false, reason: "Source PDF unchanged since last publish" };
  }

  const schedulesByZbs = await parseAllRuralZbs(pdf.bytes, sourcePdfUrl);

  const failuresByZbs: Record<string, string[]> = {};
  for (const zbsId of RURAL_ZBS_IDS) {
    const schedules: PharmacySchedule[] = schedulesByZbs[zbsId] ?? [];
    const zbsPrevious = scheduleStore.get(zbsId);
    const validationConfig = REGION_VALIDATION_CONFIG[zbsId];
    const validation = validationConfig ? validateSchedules(schedules, validationConfig, zbsPrevious) : { passed: true, failures: [] };
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
    const zbsPrevious = scheduleStore.get(zbsId);
    const nextVersion = (zbsPrevious?.version ?? 0) + 1;
    await publishLocationSchedule({
      locationId: zbsId,
      regionId: "segovia-rural",
      sourcePdfUrl,
      sourcePdfSha256: pdf.sha256,
      sourcePdfLastModified: pdf.lastModified,
      sourcePdfEtag: pdf.etag,
      parsedAt: new Date().toISOString(),
      version: nextVersion,
      schedules: schedulesByZbs[zbsId] ?? [],
    });
    publishedVersions[zbsId] = nextVersion;
  }

  return { published: true, versions: publishedVersions };
}

export async function adminRoutes(app: FastifyInstance): Promise<void> {
  app.post("/api/admin/reload", { preHandler: requireReloadKey }, async () => {
    const result = await scheduleStore.reload();
    return result;
  });

  app.post<{ Params: { locationId: string } }>(
    "/api/refresh/:locationId",
    { preHandler: requireReloadKey },
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
