package com.bfollon.farmaciasdeGuardia.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Represents a pharmacy schedule for a specific date with different duty shifts
 * Equivalent to iOS PharmacySchedule.swift
 */
@Entity(tableName = "pharmacy_schedules")
@TypeConverters(PharmacyScheduleConverters::class)
@Serializable
data class PharmacySchedule(
    @PrimaryKey
    val id: String = "${date.day}-${date.month}-${date.year}",
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
            DutyTimeSpan.CAPITAL_DAY to dayShiftPharmacies,
            DutyTimeSpan.CAPITAL_NIGHT to nightShiftPharmacies
        )
    )
    
    /**
     * Backward compatibility properties (can be removed after UI is updated)
     */
    val dayShiftPharmacies: List<Pharmacy>
        get() {
            // Try capital-specific shifts first, then fall back to full day
            return shifts[DutyTimeSpan.CAPITAL_DAY] ?: shifts[DutyTimeSpan.FULL_DAY] ?: emptyList()
        }
    
    val nightShiftPharmacies: List<Pharmacy>
        get() {
            // Try capital-specific shifts first, then fall back to full day (for 24-hour regions)
            return shifts[DutyTimeSpan.CAPITAL_NIGHT] ?: shifts[DutyTimeSpan.FULL_DAY] ?: emptyList()
        }
}

/**
 * Type converters for Room database to handle complex types
 */
class PharmacyScheduleConverters {
    @TypeConverter
    fun fromDutyDate(date: DutyDate): String {
        return Json.encodeToString(date)
    }
    
    @TypeConverter
    fun toDutyDate(dateString: String): DutyDate {
        return Json.decodeFromString(dateString)
    }
    
    @TypeConverter
    fun fromShiftsMap(shifts: Map<DutyTimeSpan, List<Pharmacy>>): String {
        return Json.encodeToString(shifts)
    }
    
    @TypeConverter
    fun toShiftsMap(shiftsString: String): Map<DutyTimeSpan, List<Pharmacy>> {
        return Json.decodeFromString(shiftsString)
    }
}
