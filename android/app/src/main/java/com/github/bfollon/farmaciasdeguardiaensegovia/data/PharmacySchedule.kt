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

package com.github.bfollon.farmaciasdeguardiaensegovia.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*

/**
 * Represents a pharmacy schedule for a specific date with different duty shifts
 * Equivalent to iOS PharmacySchedule.swift
 */
@Serializable(with = PharmacyScheduleSerializer::class)
data class PharmacySchedule(
    val date: DutyDate,
    val shifts: Map<DutyTimeSpan, List<Pharmacy>>
) {
    /**
     * Convenience constructor for backward compatibility during transition
     */
    constructor(
        date: DutyDate,
        dayShiftPharmacies: List<Pharmacy>,
        nightShiftPharmacies: List<Pharmacy>
    ) : this(
        date = date,
        shifts = mapOf(
            DutyTimeSpan.CapitalDay to dayShiftPharmacies,
            DutyTimeSpan.CapitalNight to nightShiftPharmacies
        )
    )
    
    /**
     * Backward compatibility properties (can be removed after UI is updated)
     */
    val dayShiftPharmacies: List<Pharmacy>
        get() {
            // Try capital-specific shifts first, then fall back to full day
            return shifts[DutyTimeSpan.CapitalDay] ?: shifts[DutyTimeSpan.FullDay] ?: emptyList()
        }
    
    val nightShiftPharmacies: List<Pharmacy>
        get() {
            // Try capital-specific shifts first, then fall back to full day (for 24-hour regions)
            return shifts[DutyTimeSpan.CapitalNight] ?: shifts[DutyTimeSpan.FullDay] ?: emptyList()
        }
}

/**
 * Custom serializer for PharmacySchedule that handles the Map<DutyTimeSpan, List<Pharmacy>>
 * by converting DutyTimeSpan keys to strings for JSON compatibility
 */
object PharmacyScheduleSerializer : KSerializer<PharmacySchedule> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PharmacySchedule") {
        element<DutyDate>("date")
        element<Map<String, List<Pharmacy>>>("shifts")
    }
    
    override fun serialize(encoder: Encoder, value: PharmacySchedule) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, DutyDate.serializer(), value.date)

        // Convert Map<DutyTimeSpan, List<Pharmacy>> to Map<String, List<Pharmacy>>, keyed by
        // the server's semantic shift name (matches Record<string, Pharmacy[]> wire format).
        val stringKeyShifts = value.shifts.mapKeys { (key, _) -> key.shiftKey }

        composite.encodeSerializableElement(
            descriptor, 1, 
            MapSerializer(String.serializer(), ListSerializer(Pharmacy.serializer())),
            stringKeyShifts
        )
        composite.endStructure(descriptor)
    }
    
    override fun deserialize(decoder: Decoder): PharmacySchedule {
        val composite = decoder.beginStructure(descriptor)
        var date: DutyDate? = null
        var stringKeyShifts: Map<String, List<Pharmacy>>? = null
        
        while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> date = composite.decodeSerializableElement(descriptor, 0, DutyDate.serializer())
                1 -> stringKeyShifts = composite.decodeSerializableElement(
                    descriptor, 1,
                    MapSerializer(String.serializer(), ListSerializer(Pharmacy.serializer()))
                )
                CompositeDecoder.DECODE_DONE -> break
                else -> error("Unexpected index: $index")
            }
        }
        composite.endStructure(descriptor)
        
        require(date != null) { "Missing date" }
        require(stringKeyShifts != null) { "Missing shifts" }

        // Convert Map<String, List<Pharmacy>> back to Map<DutyTimeSpan, List<Pharmacy>>.
        // Unrecognized shift keys from the server are dropped, not treated as errors.
        val dutyTimeSpanShifts = stringKeyShifts.mapNotNull { (stringKey, pharmacies) ->
            DutyTimeSpan.fromShiftKey(stringKey)?.let { it to pharmacies }
        }.toMap()

        return PharmacySchedule(date, dutyTimeSpanShifts)
    }
}
