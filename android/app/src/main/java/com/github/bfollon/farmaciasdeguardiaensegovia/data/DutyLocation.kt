package com.github.bfollon.farmaciasdeguardiaensegovia.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = DutyLocationSerializer::class)
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

        /**
         * Reconstructs a DutyLocation from its ID by looking up in Regions and ZBSs
         * This is used when deserializing from a map key
         */
        fun fromId(id: String): DutyLocation {
            val maybeLocation = Region.allRegions.find { it.id == id }?.let { fromRegion(it) }
                ?: ZBS.availableZBS.find { it.id == id }?.let { fromZBS(it) }

            maybeLocation?.let { return it }

            // If not found, throw an error
            throw IllegalArgumentException("No DutyLocation found with ID: $id")
        }
    }
}

/**
 * Custom serializer for DutyLocation that serializes only the ID
 * This enables DutyLocation to be used as a map key
 */
object DutyLocationSerializer : KSerializer<DutyLocation> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DutyLocation", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DutyLocation) {
        encoder.encodeString(value.id)
    }

    override fun deserialize(decoder: Decoder): DutyLocation {
        val id = decoder.decodeString()
        return DutyLocation.fromId(id)
    }
}
