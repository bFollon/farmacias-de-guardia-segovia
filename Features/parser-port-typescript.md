# Feature: TypeScript Parser Port

## Problem

The only PDF-parsing logic that exists today lives twice: coordinate-based in Swift (`ios/FarmaciasDeGuardiaEnSegovia/Services/PDFParsing/`, using PDFKit) and text-based in Kotlin (`android/.../services/pdfparsing/`, using iText7). Neither should be deleted — they encode months of tuning against real-world PDF quirks — but [[backend-data-service]] needs a single server-side implementation, and the server is Node/TypeScript.

## Target solution

Port the four region parsers to TypeScript, running server-side against a PDF-to-text extraction library (e.g. `pdf-parse` or `pdfjs-dist` — text-based extraction, since Node has no direct PDFKit equivalent; closer in spirit to the Android/iText7 approach than iOS's coordinate scanning). The existing Strategy Pattern carries over directly:

```
PDFProcessingService (TS)
├── PDFParsingStrategy (interface)
├── ColumnBasedPDFParser / RowBasedPDFParser (base classes)
└── strategies/
    ├── SegoviaCapitalParser.ts
    ├── CuellarParser.ts
    ├── ElEspinarParser.ts
    └── SegoviaRuralParser.ts
```

Each ported parser is checked against a **fixture PDF + expected-JSON snapshot pair** per region, captured once from the current production PDFs, so a port that silently changes behavior fails a test rather than shipping bad data.

## MVP

- Port `SegoviaCapitalParser` first — 3-column layout, day/night shifts, the most-used region and the one with the most existing test coverage to compare against.
- Cuéllar and El Espinar next — near-identical 2-column weekly-schedule structure to each other.
- `SegoviaRuralParser` last — most complex (8-ZBS subdivision, shared/separate schedules per project `CLAUDE.md`), and the one most likely to need the [[parsing-validation-gate]] fallback in practice.

## Architecture decisions

### Text-based extraction, not coordinate-based

iOS's approach (`ColumnBasedPDFParser.swift`) defines exact `CGRect` column boundaries per page layout. That's PDFKit-specific rendering geometry with no direct Node equivalent. Android's text-extraction approach (read the PDF's text stream directly, no coordinates) is architecturally closer to what's available server-side — port from the **Kotlin** implementation as the primary reference, using Swift only to cross-check edge-case handling (e.g. the `additionalInfo`/phone-number regex splitting in `Pharmacy.parse`).

### Keep parser output identical to the client Codable shape

`Pharmacy.parse` / `Pharmacy.parseBatch` logic (name must contain "FARMACIA", phone regex `Tfno:\s*\d{3}\s*\d{6}`, address = first line after name) ports near-verbatim — these are string-matching rules independent of language. Preserve them exactly rather than "improving" them during the port, so any behavior change is a deliberate, reviewable diff against the fixture snapshots, not an accidental side effect of the rewrite.

### Fixture-based regression tests are non-negotiable

InterSego abandoned automated parsing specifically because parse failures were silent and hard to trust (see `migration-plan.md`). The TS port must have a fixture PDF (checked into the repo, one real historical PDF per region) with a hand-verified expected JSON output, run in CI on every change to the parser code. This is the primary defense against silent regressions that the [[parsing-validation-gate]] can't catch (it validates *shape*, not *correctness*).

## Status

| Step | Status |
|---|---|
| Fixture PDFs + expected JSON captured (all 4 regions) | ⬜ |
| `SegoviaCapitalParser.ts` ported + passing fixture test | ⬜ |
| `CuellarParser.ts` / `ElEspinarParser.ts` ported | ⬜ |
| `SegoviaRuralParser.ts` ported (8 ZBS) | ⬜ |
| CI wired to run fixture tests on parser changes | ⬜ |
