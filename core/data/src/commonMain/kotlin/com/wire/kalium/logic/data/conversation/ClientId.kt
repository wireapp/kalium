/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.conversation

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Identifies a client registered by a user.
 *
 * This is a reference class so Kotlin/Native can expose it as a concrete type
 * instead of erasing it to `Any` in Objective-C and Swift.
 */
@Serializable(with = ClientIdSerializer::class)
data class ClientId(val value: String) {
    override fun toString(): String = value

    fun toLogString(): String = value
}

internal object ClientIdSerializer : KSerializer<ClientId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ClientId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ClientId) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): ClientId = ClientId(decoder.decodeString())
}
