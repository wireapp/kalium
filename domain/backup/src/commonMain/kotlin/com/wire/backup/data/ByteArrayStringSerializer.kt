/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.backup.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

public class ByteArrayStringSerializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("BackupDateTime", PrimitiveKind.STRING)

    @OptIn(ExperimentalStdlibApi::class)
    override fun deserialize(decoder: Decoder): ByteArray {
        return decoder.decodeString().hexToByteArray()
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(value.toHexString())
    }
}
