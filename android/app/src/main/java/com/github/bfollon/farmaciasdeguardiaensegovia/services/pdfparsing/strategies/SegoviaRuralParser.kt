/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.bfollon.farmaciasdeguardiaensegovia.services.pdfparsing.strategies

import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyDate
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyLocation
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyTimeSpan
import com.github.bfollon.farmaciasdeguardiaensegovia.data.Pharmacy
import com.github.bfollon.farmaciasdeguardiaensegovia.data.PharmacySchedule
import com.github.bfollon.farmaciasdeguardiaensegovia.data.ZBS
import com.github.bfollon.farmaciasdeguardiaensegovia.services.DebugConfig
import com.github.bfollon.farmaciasdeguardiaensegovia.services.YearDetectionService
import com.github.bfollon.farmaciasdeguardiaensegovia.services.pdfparsing.PDFParsingStrategy
import com.github.bfollon.farmaciasdeguardiaensegovia.utils.MapUtils.accumulateWith
import com.github.bfollon.farmaciasdeguardiaensegovia.utils.MapUtils.mergeWith
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import java.io.File
import kotlin.collections.emptyMap
import kotlin.collections.forEach

/**
 * Parser implementation for Segovia Rural pharmacy schedules.
 * Android equivalent of iOS SegoviaRuralParser.
 *
 * Handles the complex multi-column layout with 8 ZBS (Zonas Básicas de Salud):
 * - RIAZA SEPÚLVEDA (24h)
 * - LA GRANJA (10h-22h)
 * - LA SIERRA (10h-20h)
 * - FUENTIDUEÑA (10h-20h)
 * - CARBONERO (10h-20h)
 * - NAVAS DE LA ASUNCIÓN (10h-20h)
 * - VILLACASTÍN (10h-20h)
 * - CANTALEJO (10h-20h)
 */
class SegoviaRuralParser : PDFParsingStrategy {

    /**
     * Data class for pharmacy information
     */
    private data class PharmacyInfo(
        val name: String,
        val address: String,
        val phone: String,
        val dutyTimeSpan: DutyTimeSpan
    ) {
        fun toPharmacy() = Pharmacy(
            name = name,
            address = address,
            phone = phone,
            additionalInfo = null
        )
    }

    override fun getStrategyName(): String = "SegoviaRuralParser"

    companion object {
        // Regex pattern for Spanish date format: dd-mmm-yy
        // Matches patterns like "02-sep-25", "15-dic-24", etc.
        // Only matches valid Spanish month abbreviations
        private val SPANISH_DATE_REGEX = Regex(
            """(\d{1,2})-(ene|feb|mar|abr|may|jun|jul|ago|sep|oct|nov|dic)-(\d{2})""",
            RegexOption.IGNORE_CASE
        )

        private const val WEEKDAYS = 7

        private enum class LaGranjaPharmacyType(val pdfName: String) {
            VALENCIANA("C/ Valenciana"),
            DOLORES("Plaza los Dolores")
        }

        private object LaGranjaPharmacies {
            val valenciana = PharmacyInfo(
                name = "Farmacia Cristina Mínguez Del Pozo",
                address = "C. Valenciana, 3, BAJO, 40100 Real Sitio de San Ildefonso, Segovia",
                phone = "921470038",
                dutyTimeSpan = DutyTimeSpan.Companion.RuralExtendedDaytime
            )

            val dolores = PharmacyInfo(
                name = "Farmacia Almudena Martínez Pardo del Valle",
                address = "Plaza los de Dolores, 7, 40100 Real Sitio de San Ildefonso, Segovia",
                phone = "921472391",
                dutyTimeSpan = DutyTimeSpan.Companion.RuralExtendedDaytime
            )
        }

        private val cantalejoPharmacies = listOf(
            PharmacyInfo(
                name = "Farmacia en Cantalejo",
                address = "C. Frontón, 15, 40320 Cantalejo, Segovia",
                phone = "921520053",
                dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
            ),
            PharmacyInfo(
                name = "Farmacia Carmen Bautista",
                address = "C. Inge Martín Gil, 10, 40320 Cantalejo, Segovia",
                phone = "921520005",
                dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
            )
        )

        // Pharmacy information lookup table for Segovia Rural organized by ZBS
        // Extracted from iOS SegoviaRuralParser.swift
        private val pharmacyInfoByZBS: Map<ZBS, Map<String, PharmacyInfo>> = mapOf(
            ZBS.Companion.RIAZA_SEPULVEDA to mapOf(
                "RIAZA" to PharmacyInfo(
                    name = "Farmacia César Fernando Gutiérrez Miguel",
                    address = "C. Ricardo Provencio, 16, 40500 Riaza, Segovia",
                    phone = "921550131",
                    dutyTimeSpan = DutyTimeSpan.Companion.FullDay
                ),
                "SEPÚLVEDA" to PharmacyInfo(
                    name = "Farmacia Francisco Ruiz Carrasco",
                    address = "Pl. España, 16, 40300 Sepúlveda, Segovia",
                    phone = "921540018",
                    dutyTimeSpan = DutyTimeSpan.Companion.FullDay
                ),
                "S.E. GORMAZ (SORIA)" to PharmacyInfo(
                    name = "Farmacia Irigoyen",
                    address = "C. Escuelas, 5, 42330 San Esteban de Gormaz, Soria",
                    phone = "975350208",
                    dutyTimeSpan = DutyTimeSpan.Companion.FullDay
                ),
                "CEREZO ABAJO" to PharmacyInfo(
                    name = "Farmacia Mario Caballero Serrano",
                    address = "C. Real, 2, 40591 Cerezo de Abajo, Segovia",
                    phone = "921557110",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralExtendedDaytime
                ),
                "BOCEGUILLAS" to PharmacyInfo(
                    name = "Farmacia Lcda Mª del Pilar Villas Miguel",
                    address = "C. Bayona, 21, 40560 Boceguillas, Segovia",
                    phone = "921543849",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralExtendedDaytime
                ),
                "AYLLÓN" to PharmacyInfo(
                    name = "Farmacia Luis de la Peña Buquerin",
                    address = "Plaza Mayor, 12, 40520 Ayllón, Segovia",
                    phone = "921553003",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralExtendedDaytime
                )
            ),

            ZBS.Companion.LA_SIERRA to mapOf(
                "PRÁDENA" to PharmacyInfo(
                    name = "Farmacia Ana Belén Tomero Díez",
                    address = "Calle Pl., 18, 40165 Prádena, Segovia",
                    phone = "921507050",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "ARCONES" to PharmacyInfo(
                    name = "Farmacia Teresa Laporta Sánchez",
                    address = "Pl. Mayor, 3, 40164 Arcones, Segovia",
                    phone = "921504134",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "NAVAFRÍA" to PharmacyInfo(
                    name = "Farmacia Martín Cuesta",
                    address = "C. la Reina, 0, 40161 Navafría, Segovia",
                    phone = "921506113",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "TORREVAL" to PharmacyInfo(
                    name = "Farmacia Lda. Mónica Carrasco Herrero",
                    address = "Travesia la Fragua, 16, 40171 Torre Val de San Pedro, Segovia",
                    phone = "921506028",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                )
            ),

            ZBS.Companion.FUENTIDUENA to mapOf(
                "HONTALBILLA" to PharmacyInfo(
                    name = "Farmacia Lcdo Burgos Burgos Isabel",
                    address = "Plaza Mayor, 1, 40353 Hontalbilla, Segovia",
                    phone = "921148190",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "TORRECILLA" to PharmacyInfo(
                    name = "Farmacia Lcdo Gallego Esteban Fernando",
                    address = "C. Povedas, 6, 40359 Torrecilla del Pinar, Segovia",
                    phone = "No disponible",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "TORRECELLA" to PharmacyInfo(
                    name = "Farmacia Lcdo Gallego Esteban Fernando",
                    address = "C. Povedas, 6, 40359 Torrecilla del Pinar, Segovia",
                    phone = "No disponible",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "OLOMBRADA" to PharmacyInfo(
                    name = "Dr. Jesús Santos del Cura",
                    address = "C. Real, 3, 40220 Olombrada, Segovia",
                    phone = "921164327",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "FUENTIDUEÑA" to PharmacyInfo(
                    name = "Farmacia Fuentidueña",
                    address = "C. Real, 40, 40357 Fuentidueña, Segovia",
                    phone = "921533630",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "SACRAMENIA" to PharmacyInfo(
                    name = "Farmacia Gloria Hernando Bayón",
                    address = "C. Manuel Sanz Burgoa, 14, 40237 Sacramenia, Segovia",
                    phone = "921527501",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "FUENTESAUCO" to PharmacyInfo(
                    name = "Farmacia Paloma María Prieto Pérez",
                    address = "S N, Plaza Mercado, 0, 40355 Fuentesaúco de Fuentidueña, Segovia",
                    phone = "No disponible",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                )
            ),

            ZBS.Companion.CARBONERO to mapOf(
                "NAVALMANZANO" to PharmacyInfo(
                    name = "Farmacia Carmen I. Tomero Díez",
                    address = "Pl. Mayor, 2, 40280 Navalmanzano, Segovia",
                    phone = "921575109",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "CARBONERO M" to PharmacyInfo(
                    name = "Farmacia Carbonero",
                    address = "Pl. Pósito Real, 1, 40270 Carbonero el Mayor, Segovia",
                    phone = "921560427",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "ZARZUELA PINAR" to PharmacyInfo(
                    name = "Farmacia Maria Sol Benito Sanz",
                    address = "C/ Caño, 7, 40293 Zarzuela del Pinar (Segovia)",
                    phone = "921574621",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "ESCARABAJOSA" to PharmacyInfo(
                    name = "Farmacia GILSANZ",
                    address = "Pl. Mayor, 40291 Escarabajosa de Cabezas, Segovia",
                    phone = "921562159",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "LASTRAS DE CUÉLLAR" to PharmacyInfo(
                    name = "Farmacia Mª Antonia Sacristán Rodríguez",
                    address = "C. Rincón, 3, 40352 Lastras de Cuéllar, Segovia",
                    phone = "921169250",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "FUENTEPELAYO" to PharmacyInfo(
                    name = "Farmacia Lda. Patricia Avellón Senovilla",
                    address = "C. Santillana, 3, 40260 Fuentepelayo, Segovia",
                    phone = "921574392",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "CANTIMPALOS" to PharmacyInfo(
                    name = "Farmacia Enrique Covisa Nager",
                    address = "Pl. Mayor, 17, 40360 Cantimpalos, Segovia",
                    phone = "921496025",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "AGUILAFUENTE" to PharmacyInfo(
                    name = "Farmacia Miriam Chamorro García",
                    address = "Av. del Escultor D. Florentino Trapero, 5, 40340 Aguilafuente, Segovia",
                    phone = "921572445",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "MOZONCILLO" to PharmacyInfo(
                    name = "Farmacia Isabel Frías López",
                    address = "C. Real, 16-18, 40250 Mozoncillo, Segovia",
                    phone = "921577273",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "ESCALONA" to PharmacyInfo(
                    name = "Farmacia Matilde García García",
                    address = "C. de la Cruz, 6, 40350 Escalona del Prado, Segovia",
                    phone = "921570026",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                )
            ),

            ZBS.Companion.NAVAS_DE_LA_ASUNCION to mapOf(
                "COCA" to PharmacyInfo(
                    name = "Farmacia Ana Isabel Maroto Arenas",
                    address = "Pl. Arco, 2, 40480 Coca, Segovia",
                    phone = "921586677",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "STA. Mª REAL" to PharmacyInfo(
                    name = "Farmacia Pilar Tribiño Mendiola",
                    address = "Pl. Mayor, 11, 40440 Santa María la Real de Nieva, Segovia",
                    phone = "921594013",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "NIEVA" to PharmacyInfo(
                    name = "Farmacia María Dolores Gómez Roán",
                    address = "Calle Ayuntamiento, 12, 40447 Nieva, Segovia",
                    phone = "921594727",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "SANTIUSTE" to PharmacyInfo(
                    name = "Farmacia Lda Amparo Maroto Gomez",
                    address = "Pl. Iglesia, 5, 40460 Santiuste de San Juan Bautista, Segovia",
                    phone = "921596259",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "NAVAS DE ORO" to PharmacyInfo(
                    name = "Farmacia Cubero. Gdo. Sergio Cubero de Blas",
                    address = "C. Libertad, 1, 40470 Navas de Oro, Segovia",
                    phone = "921591585",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "NAVA DE LA A" to PharmacyInfo(
                    name = "Farmacia Ldo. Vicente Rebollo Antolín Javier",
                    address = "C. de Elías Vírseda, 3, 40450 Nava de la Asunción, Segovia",
                    phone = "921580533",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "BERNARDOS" to PharmacyInfo(
                    name = "Farmacia Lcdo Casado Rata Coral",
                    address = "Pl. Mayor, 8, 40430 Bernardos, Segovia",
                    phone = "921566012",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                )
            ),

            ZBS.Companion.VILLACASTIN to mapOf(
                "VILLACASTÍN" to PharmacyInfo(
                    name = "Farmacia Cristina Herradón Gil-Gallardo",
                    address = "Calle Iglesia, 18, 40150 Villacastín, Segovia",
                    phone = "921198173",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "ZARZUELA M." to PharmacyInfo(
                    name = "Farmacia María A. Reviriego Morcuende",
                    address = "Av. San Antonio, 2, 40152 Zarzuela del Monte, Segovia",
                    phone = "921198297",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "NAVAS DE SA" to PharmacyInfo(
                    name = "Farmacia María José Martín Barguilla",
                    address = "C. Diana, 21, 40408 Navas de San Antonio, Segovia",
                    phone = "921193128",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                ),
                "MAELLO (ÁVILA)" to PharmacyInfo(
                    name = "Farmacia Noelia Guerra García",
                    address = "Calle Vilorio, 8, 05291 Maello, Ávila",
                    phone = "921192126",
                    dutyTimeSpan = DutyTimeSpan.Companion.RuralDaytime
                )
            )
        )
    }

    override fun parseSchedules(
        pdfFile: File,
        pdfUrl: String?
    ): Map<DutyLocation, List<PharmacySchedule>> {
        println("\n=== Segovia Rural PDF Parser - Line by Line Output ===")

        // Open PDF once and reuse across all pages
        val reader = PdfReader(pdfFile)
        val pdfDoc = PdfDocument(reader)

        return try {
            val pageCount = pdfDoc.numberOfPages
            println("📄 Processing $pageCount pages of Segovia Rural PDF...")

            // Detect year from first page (always fresh detection on each parse)
            val firstPageContent = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(1))
            val yearResult = YearDetectionService.detectYear(firstPageContent, pdfUrl)
            val detectedBaseYear = yearResult.year

            yearResult.warning?.let {
                DebugConfig.debugPrint("⚠️ Year detection warning: $it")
            }

            DebugConfig.debugPrint("📅 Detected year: ${yearResult.year} (source: ${yearResult.source})")

            val (allSchedules, firstLaGranjaOccurrence) = (1..pageCount).fold(
                Pair(
                    emptyMap<DutyLocation, List<PharmacySchedule>>(),
                    null as LaGranjaPharmacyType?
                )
            ) { (acc, maybeLaGranjaPharmacy), pageIndex ->
                DebugConfig.debugPrint("📃 Processing page $pageIndex of $pageCount")

                val content = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(pageIndex))
                val lines = content.split('\n')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                val (pharmaciesZBSMap, laGranjaUpdated) = lines.fold(
                    Pair(
                        emptyMap<ZBS, List<PharmacySchedule>>(),
                        maybeLaGranjaPharmacy
                    )
                ) { (acc, maybeLaGranjaPharmacy), line ->
                    Pair(
                        acc.accumulateWith(processLine(line, detectedBaseYear)),
                        detectFirstLaGranjaPharmacy(line, maybeLaGranjaPharmacy)
                    )
                }

                val pharmaciesSchedules =
                    pharmaciesZBSMap.mapKeys { (zbs, _) -> DutyLocation.Companion.fromZBS(zbs) }

                Pair(acc.mergeWith(pharmaciesSchedules) { a, b -> a + b }, laGranjaUpdated)
            }


            allSchedules.populateLaGranjaSchedules(firstLaGranjaOccurrence)
                .populateCantalejoSchedules().also {
                it.forEach { (location, schedules) ->
                    DebugConfig.debugPrint("All schedules parsed for ${location.name}: ${schedules.size}")
                }
            }
        } catch (e: Exception) {
            println("❌ Error reading Segovia Rural PDF: ${e.message}")
            emptyMap()
        } finally {
            pdfDoc.close()
        }
    }

    private fun processLine(line: String, baseYear: Int): Map<ZBS, PharmacySchedule> {

        DebugConfig.debugPrint("Line: $line")

        val schedules = when {
            hasDate(line) -> {
                val maybeDate = extractDate(line, baseYear)
                val pharmacies = extractPharmaciesByZBS(line)

                maybeDate?.let { date ->
                    pharmacies.map { (key, value) ->
                        key to value.groupBy { it.dutyTimeSpan }
                            .mapValues { (_, pharmacyInfoList) ->
                                pharmacyInfoList
                                    .map {
                                        it.toPharmacy()
                                    }
                            }
                    }.associate { (zbs, value) ->
                        zbs to PharmacySchedule(
                            date = maybeDate,
                            shifts = value
                        )
                    }
                } ?: emptyMap<ZBS, PharmacySchedule>().also {
                    DebugConfig.debugPrint("Could not extract date from line: $line")
                }
            }

            else -> {
                DebugConfig.debugPrint("Skipping unsupported line: $line")
                emptyMap()
            }
        }

        DebugConfig.debugPrint("Parsed schedules for line: $line\n$schedules")

        return schedules
    }

    private fun hasDate(line: String): Boolean = SPANISH_DATE_REGEX.containsMatchIn(line)

    private fun extractDate(line: String, baseYear: Int): DutyDate? {
        val match = SPANISH_DATE_REGEX.find(line) ?: return null

        val dayStr = match.groupValues[1]
        val monthAbbr = match.groupValues[2].lowercase()
        val yearStr = match.groupValues[3]

        val day = dayStr.toIntOrNull() ?: return null
        val twoDigitYear = yearStr.toIntOrNull() ?: return null

        // Convert 2-digit year to 4-digit using detected base year
        // If base year ends in same 2 digits, use it; otherwise calculate offset
        val baseLastTwo = baseYear % 100
        val year = when {
            baseLastTwo == twoDigitYear -> baseYear
            twoDigitYear < baseLastTwo -> baseYear - (baseLastTwo - twoDigitYear)
            else -> baseYear + (twoDigitYear - baseLastTwo)
        }

        val monthName = when (monthAbbr) {
            "ene" -> "enero"
            "feb" -> "febrero"
            "mar" -> "marzo"
            "abr" -> "abril"
            "may" -> "mayo"
            "jun" -> "junio"
            "jul" -> "julio"
            "ago" -> "agosto"
            "sep" -> "septiembre"
            "oct" -> "octubre"
            "nov" -> "noviembre"
            "dic" -> "diciembre"
            else -> return null
        }

        return DutyDate(
            dayOfWeek = "Unknown", // Will be calculated later if needed
            day = day,
            month = monthName,
            year = year
        )
    }

    private fun extractPharmaciesByZBS(line: String): Map<ZBS, List<PharmacyInfo>> =
        pharmacyInfoByZBS.map { (zbs, pharmacies) ->
            zbs to pharmacies.filterKeys { key ->
                line.lowercase().contains(key.lowercase())
            }.values.toList()
        }.toMap()

    private fun detectFirstLaGranjaPharmacy(
        line: String,
        previouslyDetected: LaGranjaPharmacyType?
    ): LaGranjaPharmacyType? {
        return if (previouslyDetected != null) {
            previouslyDetected
        } else {
            val valencianaIndex = line.indexOf(LaGranjaPharmacyType.VALENCIANA.pdfName)
            val doloresIndex = line.indexOf(LaGranjaPharmacyType.DOLORES.pdfName)

            when {
                valencianaIndex < doloresIndex -> LaGranjaPharmacyType.VALENCIANA
                doloresIndex < valencianaIndex -> LaGranjaPharmacyType.DOLORES
                else -> null
            }
        }
    }

    private fun Map<DutyLocation, List<PharmacySchedule>>.populateLaGranjaSchedules(
        firstLaGranjaOccurrence: LaGranjaPharmacyType?
    ): Map<DutyLocation, List<PharmacySchedule>> {
        return if (firstLaGranjaOccurrence == null) {
            this
        } else {
            this@populateLaGranjaSchedules[DutyLocation.Companion.fromZBS(ZBS.Companion.NAVAS_DE_LA_ASUNCION)]?.let { sampleSchedules ->
                val (oddPharmacy, evenPharmacy) = when (firstLaGranjaOccurrence) {
                    LaGranjaPharmacyType.VALENCIANA -> Pair(
                        LaGranjaPharmacies.valenciana,
                        LaGranjaPharmacies.dolores
                    )

                    LaGranjaPharmacyType.DOLORES -> Pair(
                        LaGranjaPharmacies.dolores,
                        LaGranjaPharmacies.valenciana
                    )
                }

                val laGranjaSchedules = sampleSchedules.mapIndexed { index, value ->
                    val weekNumber = (index / 7) + 1  // 1-based week numbering
                    val isEvenWeek = (weekNumber and 1) == 0  // Bitwise check

                    when (isEvenWeek) {
                        true -> value.copy(
                            shifts = mapOf(
                                evenPharmacy.dutyTimeSpan to listOf(
                                    evenPharmacy.toPharmacy()
                                )
                            )
                        )

                        false -> value.copy(
                            shifts = mapOf(
                                evenPharmacy.dutyTimeSpan to listOf(
                                    oddPharmacy.toPharmacy()
                                )
                            )
                        )
                    }
                }

                this + (DutyLocation.Companion.fromZBS(ZBS.Companion.LA_GRANJA) to laGranjaSchedules)
            } ?: run {
                DebugConfig.debugPrint("Could not get sample schedules to populate La Granja")
                this@populateLaGranjaSchedules
            }
        }
    }

    fun Map<DutyLocation, List<PharmacySchedule>>.populateCantalejoSchedules(): Map<DutyLocation, List<PharmacySchedule>> =
        this[DutyLocation.Companion.fromZBS(ZBS.Companion.NAVAS_DE_LA_ASUNCION)]?.let { sampleSchedules ->
            val cantalejoSchedules = sampleSchedules.map { schedule ->
                schedule.copy(
                    shifts = mapOf(
                        DutyTimeSpan.Companion.RuralDaytime to cantalejoPharmacies.map { it.toPharmacy() }
                    ))
            }

            this + (DutyLocation.Companion.fromZBS(ZBS.Companion.CANTALEJO) to cantalejoSchedules)
        } ?: run {
            DebugConfig.debugPrint("Could not get sample schedules to populate La Cantalejo")
            this
        }
}
