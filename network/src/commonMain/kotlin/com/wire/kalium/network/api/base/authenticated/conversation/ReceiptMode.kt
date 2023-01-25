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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ReceiptMode.ReceiptModeAsIntSerializer::class)
enum class ReceiptMode(val value: Int) {
    DISABLED(0),
    ENABLED(1);

    object ReceiptModeAsIntSerializer : KSerializer<ReceiptMode> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ReceiptMode", PrimitiveKind.INT)
        override fun serialize(encoder: Encoder, value: ReceiptMode) {
            encoder.encodeInt(value.value)
        }

        override fun deserialize(decoder: Decoder): ReceiptMode {
            val value = decoder.decodeInt()
            return if (value > 0) ENABLED else DISABLED
        }
    }
}
