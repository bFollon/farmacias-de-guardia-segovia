import { createHash } from "node:crypto";
import { detectYear, getCurrentYear } from "./yearDetection.js";
import { monthToNumber, monthNumberToName, spanishDayOfWeek } from "./monthMap.js";
import type { DutyDate, Pharmacy, PharmacySchedule } from "../types.js";

/**
 * Port of android's SegoviaRuralParser.kt (the most complex of the four — 8 ZBS,
 * of which only 6 are directly parseable from the calendar grid; La Granja and
 * Cantalejo are derived). Ported the pharmacy data table and the 6-real-ZBS keyword
 * extraction near-verbatim, but La Granja's rotation detection is NOT a port of
 * detectFirstLaGranjaPharmacy() — see below for why.
 */

interface RuralPharmacyInfo {
  name: string;
  address: string;
  phone: string;
  shiftKey: "fullDay" | "ruralDaytime" | "ruralExtendedDaytime";
}

/** The 6 ZBS directly identifiable from the calendar grid by town-name keyword. */
const REAL_ZBS_PHARMACY_INFO: Record<string, Record<string, RuralPharmacyInfo>> = {
  "riaza-sepulveda": {
    RIAZA: { name: "Farmacia César Fernando Gutiérrez Miguel", address: "C. Ricardo Provencio, 16, 40500 Riaza, Segovia", phone: "921550131", shiftKey: "fullDay" },
    "SEPÚLVEDA": { name: "Farmacia Francisco Ruiz Carrasco", address: "Pl. España, 16, 40300 Sepúlveda, Segovia", phone: "921540018", shiftKey: "fullDay" },
    "S.E. GORMAZ (SORIA)": { name: "Farmacia Irigoyen", address: "C. Escuelas, 5, 42330 San Esteban de Gormaz, Soria", phone: "975350208", shiftKey: "fullDay" },
    // Both clients key this "CEREZO ABAJO" (no "DE"), which never matches the real PDF's
    // "CEREZO DE ABAJO" — a live bug that drops Riaza/Sepúlveda's whole schedule for
    // every week this town is on duty (confirmed against fixtures/segovia-rural.pdf: ~2
    // multi-day gaps/year). Fixed here rather than ported forward; flagged for the clients.
    "CEREZO DE ABAJO": { name: "Farmacia Mario Caballero Serrano", address: "C. Real, 2, 40591 Cerezo de Abajo, Segovia", phone: "921557110", shiftKey: "ruralExtendedDaytime" },
    BOCEGUILLAS: { name: "Farmacia Lcda Mª del Pilar Villas Miguel", address: "C. Bayona, 21, 40560 Boceguillas, Segovia", phone: "921543849", shiftKey: "ruralExtendedDaytime" },
    "AYLLÓN": { name: "Farmacia Luis de la Peña Buquerin", address: "Plaza Mayor, 12, 40520 Ayllón, Segovia", phone: "921553003", shiftKey: "ruralExtendedDaytime" },
  },
  "la-sierra": {
    "PRÁDENA": { name: "Farmacia Ana Belén Tomero Díez", address: "Calle Pl., 18, 40165 Prádena, Segovia", phone: "921507050", shiftKey: "ruralDaytime" },
    ARCONES: { name: "Farmacia Teresa Laporta Sánchez", address: "Pl. Mayor, 3, 40164 Arcones, Segovia", phone: "921504134", shiftKey: "ruralDaytime" },
    "NAVAFRÍA": { name: "Farmacia Martín Cuesta", address: "C. la Reina, 0, 40161 Navafría, Segovia", phone: "921506113", shiftKey: "ruralDaytime" },
    TORREVAL: { name: "Farmacia Lda. Mónica Carrasco Herrero", address: "Travesia la Fragua, 16, 40171 Torre Val de San Pedro, Segovia", phone: "921506028", shiftKey: "ruralDaytime" },
  },
  "fuentiduena": {
    HONTALBILLA: { name: "Farmacia Lcdo Burgos Burgos Isabel", address: "Plaza Mayor, 1, 40353 Hontalbilla, Segovia", phone: "921148190", shiftKey: "ruralDaytime" },
    TORRECILLA: { name: "Farmacia Lcdo Gallego Esteban Fernando", address: "C. Povedas, 6, 40359 Torrecilla del Pinar, Segovia", phone: "No disponible", shiftKey: "ruralDaytime" },
    TORRECELLA: { name: "Farmacia Lcdo Gallego Esteban Fernando", address: "C. Povedas, 6, 40359 Torrecilla del Pinar, Segovia", phone: "No disponible", shiftKey: "ruralDaytime" },
    OLOMBRADA: { name: "Dr. Jesús Santos del Cura", address: "C. Real, 3, 40220 Olombrada, Segovia", phone: "921164327", shiftKey: "ruralDaytime" },
    "FUENTIDUEÑA": { name: "Farmacia Fuentidueña", address: "C. Real, 40, 40357 Fuentidueña, Segovia", phone: "921533630", shiftKey: "ruralDaytime" },
    SACRAMENIA: { name: "Farmacia Gloria Hernando Bayón", address: "C. Manuel Sanz Burgoa, 14, 40237 Sacramenia, Segovia", phone: "921527501", shiftKey: "ruralDaytime" },
    FUENTESAUCO: { name: "Farmacia Paloma María Prieto Pérez", address: "S N, Plaza Mercado, 0, 40355 Fuentesaúco de Fuentidueña, Segovia", phone: "No disponible", shiftKey: "ruralDaytime" },
  },
  carbonero: {
    NAVALMANZANO: { name: "Farmacia Carmen I. Tomero Díez", address: "Pl. Mayor, 2, 40280 Navalmanzano, Segovia", phone: "921575109", shiftKey: "ruralDaytime" },
    "CARBONERO M": { name: "Farmacia Carbonero", address: "Pl. Pósito Real, 1, 40270 Carbonero el Mayor, Segovia", phone: "921560427", shiftKey: "ruralDaytime" },
    "ZARZUELA PINAR": { name: "Farmacia Maria Sol Benito Sanz", address: "C/ Caño, 7, 40293 Zarzuela del Pinar (Segovia)", phone: "921574621", shiftKey: "ruralDaytime" },
    ESCARABAJOSA: { name: "Farmacia GILSANZ", address: "Pl. Mayor, 40291 Escarabajosa de Cabezas, Segovia", phone: "921562159", shiftKey: "ruralDaytime" },
    "LASTRAS DE CUÉLLAR": { name: "Farmacia Mª Antonia Sacristán Rodríguez", address: "C. Rincón, 3, 40352 Lastras de Cuéllar, Segovia", phone: "921169250", shiftKey: "ruralDaytime" },
    FUENTEPELAYO: { name: "Farmacia Lda. Patricia Avellón Senovilla", address: "C. Santillana, 3, 40260 Fuentepelayo, Segovia", phone: "921574392", shiftKey: "ruralDaytime" },
    CANTIMPALOS: { name: "Farmacia Enrique Covisa Nager", address: "Pl. Mayor, 17, 40360 Cantimpalos, Segovia", phone: "921496025", shiftKey: "ruralDaytime" },
    AGUILAFUENTE: { name: "Farmacia Miriam Chamorro García", address: "Av. del Escultor D. Florentino Trapero, 5, 40340 Aguilafuente, Segovia", phone: "921572445", shiftKey: "ruralDaytime" },
    MOZONCILLO: { name: "Farmacia Isabel Frías López", address: "C. Real, 16-18, 40250 Mozoncillo, Segovia", phone: "921577273", shiftKey: "ruralDaytime" },
    ESCALONA: { name: "Farmacia Matilde García García", address: "C. de la Cruz, 6, 40350 Escalona del Prado, Segovia", phone: "921570026", shiftKey: "ruralDaytime" },
  },
  "navas-asuncion": {
    COCA: { name: "Farmacia Ana Isabel Maroto Arenas", address: "Pl. Arco, 2, 40480 Coca, Segovia", phone: "921586677", shiftKey: "ruralDaytime" },
    "STA. Mª REAL": { name: "Farmacia Pilar Tribiño Mendiola", address: "Pl. Mayor, 11, 40440 Santa María la Real de Nieva, Segovia", phone: "921594013", shiftKey: "ruralDaytime" },
    NIEVA: { name: "Farmacia María Dolores Gómez Roán", address: "Calle Ayuntamiento, 12, 40447 Nieva, Segovia", phone: "921594727", shiftKey: "ruralDaytime" },
    SANTIUSTE: { name: "Farmacia Lda Amparo Maroto Gomez", address: "Pl. Iglesia, 5, 40460 Santiuste de San Juan Bautista, Segovia", phone: "921596259", shiftKey: "ruralDaytime" },
    "NAVAS DE ORO": { name: "Farmacia Cubero. Gdo. Sergio Cubero de Blas", address: "C. Libertad, 1, 40470 Navas de Oro, Segovia", phone: "921591585", shiftKey: "ruralDaytime" },
    "NAVA DE LA A": { name: "Farmacia Ldo. Vicente Rebollo Antolín Javier", address: "C. de Elías Vírseda, 3, 40450 Nava de la Asunción, Segovia", phone: "921580533", shiftKey: "ruralDaytime" },
    BERNARDOS: { name: "Farmacia Lcdo Casado Rata Coral", address: "Pl. Mayor, 8, 40430 Bernardos, Segovia", phone: "921566012", shiftKey: "ruralDaytime" },
  },
  villacastin: {
    "VILLACASTÍN": { name: "Farmacia Cristina Herradón Gil-Gallardo", address: "Calle Iglesia, 18, 40150 Villacastín, Segovia", phone: "921198173", shiftKey: "ruralDaytime" },
    "ZARZUELA M.": { name: "Farmacia María A. Reviriego Morcuende", address: "Av. San Antonio, 2, 40152 Zarzuela del Monte, Segovia", phone: "921198297", shiftKey: "ruralDaytime" },
    "NAVAS DE SA": { name: "Farmacia María José Martín Barguilla", address: "C. Diana, 21, 40408 Navas de San Antonio, Segovia", phone: "921193128", shiftKey: "ruralDaytime" },
    "MAELLO (ÁVILA)": { name: "Farmacia Noelia Guerra García", address: "Calle Vilorio, 8, 05291 Maello, Ávila", phone: "921192126", shiftKey: "ruralDaytime" },
  },
};

const LA_GRANJA_PHARMACIES = {
  valenciana: {
    name: "Farmacia Cristina Mínguez Del Pozo",
    address: "C. Valenciana, 3, BAJO, 40100 Real Sitio de San Ildefonso, Segovia",
    phone: "921470038",
    shiftKey: "ruralExtendedDaytime" as const,
  },
  dolores: {
    name: "Farmacia Almudena Martínez Pardo del Valle",
    address: "Plaza los de Dolores, 7, 40100 Real Sitio de San Ildefonso, Segovia",
    phone: "921472391",
    shiftKey: "ruralExtendedDaytime" as const,
  },
};

/** Text markers identifying which La Granja pharmacy a caption refers to. */
const LA_GRANJA_MARKERS: [marker: string, pharmacy: keyof typeof LA_GRANJA_PHARMACIES][] = [
  ["C/ Valenciana", "valenciana"],
  ["Plaza los Dolores", "dolores"],
];

const CANTALEJO_PHARMACIES: RuralPharmacyInfo[] = [
  { name: "Farmacia en Cantalejo", address: "C. Frontón, 15, 40320 Cantalejo, Segovia", phone: "921520053", shiftKey: "ruralDaytime" },
  { name: "Farmacia Carmen Bautista", address: "C. Inge Martín Gil, 10, 40320 Cantalejo, Segovia", phone: "921520005", shiftKey: "ruralDaytime" },
];

/** The ZBS whose directly-parsed dates serve as the canonical date range for the two derived ZBS (La Granja, Cantalejo), matching android's choice. */
const SAMPLE_ZBS_ID = "navas-asuncion";

const DATE_REGEX = /(\d{1,2})-(ene|feb|mar|abr|may|jun|jul|ago|sep|oct|nov|dic)-(\d{2})/i;

function extractDate(line: string, baseYear: number): DutyDate | undefined {
  const match = DATE_REGEX.exec(line);
  if (!match) return undefined;

  const day = Number(match[1]);
  const monthAbbr = match[2].toLowerCase();
  const twoDigitYear = Number(match[3]);

  const monthAbbrToName: Record<string, string> = {
    ene: "enero", feb: "febrero", mar: "marzo", abr: "abril", may: "mayo", jun: "junio",
    jul: "julio", ago: "agosto", sep: "septiembre", oct: "octubre", nov: "noviembre", dic: "diciembre",
  };
  const monthName = monthAbbrToName[monthAbbr];
  if (!monthName) return undefined;
  const month = monthToNumber(monthName)!;

  // Convert 2-digit year to 4-digit using the detected base year, same offset logic as
  // SegoviaRuralParser.kt's extractDate — closest 4-digit year to baseYear ending in twoDigitYear.
  const baseLastTwo = baseYear % 100;
  const year =
    baseLastTwo === twoDigitYear
      ? baseYear
      : twoDigitYear < baseLastTwo
        ? baseYear - (baseLastTwo - twoDigitYear)
        : baseYear + (twoDigitYear - baseLastTwo);

  return { dayOfWeek: spanishDayOfWeek(day, month, year), day, month: monthNumberToName(month)!, year };
}

function stablePharmacyId(zbsId: string, date: DutyDate, shiftKey: string, name: string): string {
  const key = `${zbsId}|${date.year}-${date.month}-${date.day}|${shiftKey}|${name}`;
  return createHash("sha256").update(key).digest("hex").slice(0, 32);
}

function toPharmacy(zbsId: string, date: DutyDate, info: RuralPharmacyInfo): Pharmacy {
  return {
    id: stablePharmacyId(zbsId, date, info.shiftKey, info.name),
    name: info.name,
    address: info.address,
    phone: info.phone,
    additionalInfo: null,
  };
}

export async function parseSegoviaRural(pages: string[][], pdfUrl?: string): Promise<Record<string, PharmacySchedule[]>> {
  const firstPageText = pages[0]?.join("\n") ?? "";
  const yearResult = detectYear(firstPageText, pdfUrl);
  const baseYear = yearResult.year ?? getCurrentYear();

  const schedulesByZbs: Record<string, PharmacySchedule[]> = {};
  for (const zbsId of Object.keys(REAL_ZBS_PHARMACY_INFO)) schedulesByZbs[zbsId] = [];

  // (dayIndex relative to the sample ZBS's first date, pharmacy) — used below to derive
  // La Granja's rotation. NOT a port of detectFirstLaGranjaPharmacy(): that Kotlin function
  // compares indexOf() of the two marker strings on a SINGLE line, treating "not found"
  // (-1) as "occurs earliest" — which is backwards, and on this region's real PDF most
  // marker lines contain only ONE of the two names, so it picks the wrong pharmacy. This
  // instead collects every (date, pharmacy) observation across the whole document — most
  // weeks carry one — and takes a majority vote per week-parity, which self-corrects
  // against the couple of stray duplicate captions the real PDF has near page boundaries
  // (verified against live device data: 2026-08-23 -> Farmacia Cristina Mínguez Del Pozo).
  const laGranjaObservations: { date: DutyDate; pharmacy: keyof typeof LA_GRANJA_PHARMACIES }[] = [];

  for (const pageLines of pages) {
    for (const line of pageLines) {
      const date = extractDate(line, baseYear);
      if (!date) continue;

      const lowerLine = line.toLowerCase();
      for (const [zbsId, pharmacyTable] of Object.entries(REAL_ZBS_PHARMACY_INFO)) {
        const matched = Object.entries(pharmacyTable).filter(([key]) => lowerLine.includes(key.toLowerCase()));
        if (matched.length === 0) continue;

        const shifts: PharmacySchedule["shifts"] = {};
        for (const [, info] of matched) {
          (shifts[info.shiftKey] ??= []).push(toPharmacy(zbsId, date, info));
        }
        schedulesByZbs[zbsId].push({ date, shifts });
      }

      for (const [marker, pharmacy] of LA_GRANJA_MARKERS) {
        if (line.includes(marker)) laGranjaObservations.push({ date, pharmacy });
      }
    }
  }

  for (const zbsId of Object.keys(schedulesByZbs)) {
    schedulesByZbs[zbsId].sort((a, b) => compareDates(a.date, b.date));
  }

  const sampleSchedules = schedulesByZbs[SAMPLE_ZBS_ID];
  if (sampleSchedules.length > 0) {
    schedulesByZbs["la-granja"] = deriveLaGranjaSchedules(sampleSchedules, laGranjaObservations);
    schedulesByZbs.cantalejo = deriveCantalejoSchedules(sampleSchedules);
  }

  return schedulesByZbs;
}

function compareDates(a: DutyDate, b: DutyDate): number {
  if (a.year !== b.year) return a.year - b.year;
  const aMonth = monthToNumber(a.month) ?? 0;
  const bMonth = monthToNumber(b.month) ?? 0;
  if (aMonth !== bMonth) return aMonth - bMonth;
  return a.day - b.day;
}

function toEpochDay(date: DutyDate): number {
  const month = monthToNumber(date.month) ?? 1;
  return Math.floor(Date.UTC(date.year, month - 1, date.day) / 86400000);
}

function deriveLaGranjaSchedules(
  sampleSchedules: PharmacySchedule[],
  observations: { date: DutyDate; pharmacy: keyof typeof LA_GRANJA_PHARMACIES }[],
): PharmacySchedule[] {
  const startEpochDay = toEpochDay(sampleSchedules[0].date);

  // Majority vote per week-parity (0 = weeks 0,2,4..., 1 = weeks 1,3,5... relative to the
  // sample range's first date).
  const votes: [Record<string, number>, Record<string, number>] = [{}, {}];
  for (const obs of observations) {
    const week = Math.floor((toEpochDay(obs.date) - startEpochDay) / 7);
    const parity = ((week % 2) + 2) % 2;
    votes[parity][obs.pharmacy] = (votes[parity][obs.pharmacy] ?? 0) + 1;
  }

  const pharmacyForParity = (parity: 0 | 1): keyof typeof LA_GRANJA_PHARMACIES => {
    const tally = votes[parity];
    const entries = Object.entries(tally) as [keyof typeof LA_GRANJA_PHARMACIES, number][];
    if (entries.length === 0) return parity === 0 ? "dolores" : "valenciana"; // fallback if no captions were found at all
    return entries.sort((a, b) => b[1] - a[1])[0][0];
  };

  const parity0Pharmacy = pharmacyForParity(0);
  const parity1Pharmacy = pharmacyForParity(1);

  return sampleSchedules.map((sample) => {
    const week = Math.floor((toEpochDay(sample.date) - startEpochDay) / 7);
    const parity = ((week % 2) + 2) % 2;
    const pharmacyKey = parity === 0 ? parity0Pharmacy : parity1Pharmacy;
    const info = LA_GRANJA_PHARMACIES[pharmacyKey];
    return {
      date: sample.date,
      shifts: { [info.shiftKey]: [toPharmacy("la-granja", sample.date, info)] },
    };
  });
}

function deriveCantalejoSchedules(sampleSchedules: PharmacySchedule[]): PharmacySchedule[] {
  return sampleSchedules.map((sample) => ({
    date: sample.date,
    shifts: { ruralDaytime: CANTALEJO_PHARMACIES.map((info) => toPharmacy("cantalejo", sample.date, info)) },
  }));
}

