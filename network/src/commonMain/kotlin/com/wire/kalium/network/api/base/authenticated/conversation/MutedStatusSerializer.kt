/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.network.api.base.authenticated.conversation

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@OptIn(ExperimentalSerializationApi::class)
@Serializer(MutedStatus::class)
class MutedStatusSerializer : KSerializer<MutedStatus?> {
    override val descriptor = PrimitiveSerialDescriptor("otr_muted_status", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: MutedStatus?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeInt(value.ordinal)
        }
    }

    override fun deserialize(decoder: Decoder): MutedStatus? {
        val rawValue = decoder.decodeInt()
        return MutedStatus.fromOrdinal(rawValue)
    }
}
