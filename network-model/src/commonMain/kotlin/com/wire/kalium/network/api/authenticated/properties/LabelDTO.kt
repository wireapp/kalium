/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.network.api.authenticated.properties

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class LabelListResponseDTO(
    @SerialName("labels") val labels: List<LabelDTO>
)

@Serializable
data class LabelDTO(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @Serializable(with = LabelTypeSerializer::class)
    @SerialName("type") val type: LabelType,
    @SerialName("conversations") val conversations: List<String>
)

enum class LabelType(val value: Int) {
    USER(0),
    FAVORITE(1)
}

object LabelTypeSerializer : KSerializer<LabelType> {
    override val descriptor = PrimitiveSerialDescriptor("type", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: LabelType) {
        encoder.encodeInt(value.value)
    }

    override fun deserialize(decoder: Decoder): LabelType {
        val value = decoder.decodeInt()
        return if (value == 1) {
            LabelType.FAVORITE
        } else {
            LabelType.USER
        }
    }
}
