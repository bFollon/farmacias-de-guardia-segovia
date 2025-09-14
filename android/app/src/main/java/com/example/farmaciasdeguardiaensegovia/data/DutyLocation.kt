package com.example.farmaciasdeguardiaensegovia.data

import kotlinx.serialization.Serializable

@Serializable
data class DutyLocation(
    /** Unique identifier for the ZBS */
    val id: String,

    /** Display name of the ZBS */
    val name: String,

    /** Emoji icon representing the ZBS area */
    val icon: String,

    /** Additional notes about this ZBS */
    val notes: String? = null,

    val associatedRegion: Region
) {
    companion object {
        fun fromZBS(zbs: ZBS) = DutyLocation(
            id = zbs.id,
            name = zbs.name,
            icon = zbs.icon,
            notes = zbs.notes,
            associatedRegion = Region.segoviaRural
        )

        fun fromRegion(region: Region) = DutyLocation(
            id = region.id,
            name = region.name,
            icon = region.icon,
            notes = null,
            associatedRegion = region
        )
    }
}
