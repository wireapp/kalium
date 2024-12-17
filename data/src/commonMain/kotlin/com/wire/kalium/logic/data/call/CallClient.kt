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

package com.wire.kalium.logic.data.call

import com.wire.kalium.util.serialization.LenientJsonSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class CallClient(
    @SerialName("userid") val userId: String,
    @SerialName("clientid") val clientId: String,
    @SerialName("in_subconv") val isMemberOfSubconversation: Boolean = false,
    @SerialName("quality")
    @Serializable(with = CallQuality.CallQualityAsIntSerializer::class)
    val quality: CallQuality = CallQuality.LOW
)

@Serializable
data class CallClientList(
    @SerialName("clients") val clients: List<CallClient>
) {
    fun toJsonString(): String = LenientJsonSerializer.json.encodeToString(serializer(), this)
}

enum class CallQuality {
    ANY,
    LOW,
    HIGH;

    data object CallQualityAsIntSerializer : KSerializer<CallQuality> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("quality", PrimitiveKind.INT).nullable

        override fun serialize(encoder: Encoder, value: CallQuality) {
            encoder.encodeInt(value.ordinal)
        }

        @OptIn(ExperimentalSerializationApi::class)
        override fun deserialize(decoder: Decoder): CallQuality {
            val value = if (decoder.decodeNotNullMark()) decoder.decodeInt() else 0
            return when (value) {
                1 -> LOW
                2 -> HIGH
                else -> ANY
            }
        }
    }
}
