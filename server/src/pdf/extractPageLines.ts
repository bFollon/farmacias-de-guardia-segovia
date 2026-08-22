import { getDocument, GlobalWorkerOptions } from "pdfjs-dist/legacy/build/pdf.mjs";

// pdfjs-dist needs a worker script path even in Node, where it runs the worker
// in-process rather than a real Worker thread.
GlobalWorkerOptions.workerSrc = new URL(
  "../../node_modules/pdfjs-dist/legacy/build/pdf.worker.mjs",
  import.meta.url,
).pathname;

interface TextItem {
  str: string;
  transform: number[];
}

/**
 * Extracts each page of a PDF as an array of trimmed, non-empty text lines, in
 * top-to-bottom reading order — the same shape SegoviaCapitalParser.kt gets from
 * iText7's PdfTextExtractor.getTextFromPage(...).split('\n'). Text items are
 * clustered into lines by their y position (PDF coordinates grow upward, so
 * pages read top-to-bottom as descending y) and ordered left-to-right within a line.
 */
export async function extractPdfPageLines(pdfBytes: Uint8Array): Promise<string[][]> {
  const doc = await getDocument({ data: pdfBytes }).promise;
  const pages: string[][] = [];

  try {
    for (let pageNumber = 1; pageNumber <= doc.numPages; pageNumber++) {
      const page = await doc.getPage(pageNumber);
      const textContent = await page.getTextContent();

      const items = (textContent.items as TextItem[]).filter((item) => item.str.trim().length > 0);

      // Cluster into lines by y, tolerating sub-pixel jitter between glyphs on the same baseline.
      const lineTolerance = 2;
      const lines: { y: number; items: TextItem[] }[] = [];

      for (const item of items) {
        const y = item.transform[5];
        const line = lines.find((l) => Math.abs(l.y - y) <= lineTolerance);
        if (line) {
          line.items.push(item);
        } else {
          lines.push({ y, items: [item] });
        }
      }

      lines.sort((a, b) => b.y - a.y);

      const pageLines = lines.map((line) =>
        line.items
          .sort((a, b) => a.transform[4] - b.transform[4])
          .map((item) => item.str)
          .join(" ")
          .replace(/\s+/g, " ")
          .trim(),
      );

      pages.push(pageLines.filter((line) => line.length > 0));
      await page.cleanup();
    }
  } finally {
    await doc.destroy();
  }

  return pages;
}
