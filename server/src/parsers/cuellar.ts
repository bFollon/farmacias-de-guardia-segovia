import { parseWeeklyRotation, type PharmacyInfo, type WeeklyRotationOptions } from "./weeklyRotation.js";
import type { PharmacySchedule } from "../types.js";

/** Ported from android's CuellarParser.kt — see weeklyRotation.ts for what differs and why. */
const PHARMACY_INFO: Record<string, PharmacyInfo> = {
  "Av C.J. CELA": {
    name: "Farmacia Fernando Redondo",
    address: "Av. Camilo Jose Cela, 46, 40200 Cuéllar, Segovia",
    phone: "No disponible",
  },
  "Ctra. BAHABON": {
    name: "Farmacia San Andrés",
    address: "Ctra. Bahabón, 9, 40200 Cuéllar, Segovia",
    phone: "921144794",
  },
  "C/ RESINA": {
    name: "Farmacia Ldo. Fco. Javier Alcaraz García de la Barrera",
    address: "C. Resina, 14, 40200 Cuéllar, Segovia",
    phone: "921144812",
  },
  "STA. MARINA": {
    name: "Farmacia Ldo. César Cabrerizo Izquierdo",
    address: "Calle Sta. Marina, 5, 40200 Cuéllar, Segovia",
    phone: "921140606",
  },
};

// extractPageLines.ts already collapses all whitespace (including NBSP etc.) to single
// regular spaces, so unlike CuellarParser.kt's normalizeWhitespace() no extra step is
// needed here beyond a case-insensitive substring match.
function identifyPharmacy(line: string): string | undefined {
  const normalized = line.toLowerCase();
  return Object.keys(PHARMACY_INFO).find((key) => normalized.includes(key.toLowerCase()));
}

/**
 * The PDF drops the regular dd-mmm grid for the one week straddling August/September
 * (e.g. "DOMINGO 30 DE AGOSTO Y LUNES 31 DE AGO C/ RESINA", "MARTES 1 Y MIERCOLES 2 de
 * SEPTIEMBRE STA. MARINA") — spelled-out weekday names instead. Confirmed via a real 2026
 * fixture: without this, that whole week (30 ago - 6 sep) silently disappears — an
 * already-live-but-undiagnosed gap in android's CuellarParser.kt (its docstring claims to
 * "handle" this format but no such regex exists in the code; iOS's CuellarParser.swift
 * does implement it via parseSeptemberTransition(from:), which this ports).
 */
const TRANSITION_DATE_REGEX =
  /(?:(\d+)\s+DE\s+(AGOSTO|SEPTIEMBRE|Septiembre)|(?:LUNES|MARTES|MIERCOLES|JUEVES|VIERNES|SABADO|DOMINGO)\s+(\d+)(?:\s+[Dd][Ee]\s+(?:SEPTIEMBRE|Septiembre))?)/g;

function extraDateTokens(line: string): string[] {
  if (!/DOMINGO|MARTES|JUEVES|SABADO/.test(line)) return [];

  const isSeptember = /SEPTIEMBRE|Septiembre/.test(line);
  const tokens: string[] = [];

  for (const match of line.matchAll(TRANSITION_DATE_REGEX)) {
    const [, dayWithMonth, month, dayOnly] = match;
    const day = dayWithMonth ?? dayOnly;
    const monthAbbr = (dayWithMonth ? month : isSeptember ? "septiembre" : "agosto").slice(0, 3).toLowerCase();
    if (day) tokens.push(`${day.padStart(2, "0")}-${monthAbbr}`);
  }

  return tokens;
}

const OPTIONS: WeeklyRotationOptions = {
  regionLabel: "cuellar",
  identifyPharmacy,
  pharmacyInfo: PHARMACY_INFO,
  dedupeDatesPerWeek: false,
  extraDateTokens,
};

export async function parseCuellar(pages: string[][], pdfUrl?: string): Promise<PharmacySchedule[]> {
  return parseWeeklyRotation(pages, OPTIONS, pdfUrl);
}
