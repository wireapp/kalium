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
package com.wire.kalium.network.api.authenticated.conversation

import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
sealed interface ConversationHistorySettingsDTO {
    /**
     * Conversation's history should not be shared with new members.
     */
    @Serializable
    @SerialName("private")
    data object Private : ConversationHistorySettingsDTO

    /**
     * Conversation's history should be shared with new members.
     * All messages that are less than [depth] old should be shared with new members when they join.
     */
    @Serializable
    @SerialName("shared")
    data class SharedWithNewMembers(
        @Serializable(with = ConversationHistoryDepthSerializer::class)
        @SerialName("depth") val depth: Duration
    ) : ConversationHistorySettingsDTO
}

@JvmInline
value class HistoryClientId(val value: String) {
    init {
        require(value.isNotBlank()) { "HistoryClient id cannot be blank!" }
    }
}

/**
 * Response from the server containing a page of conversation history.
 * @param events List of events on the page.
 * @param nextOffset Offset of the next page. Null if there is no next page.
 */
@Serializable
data class ConversationHistoryResponse(
    @SerialName("events")
    val events: List<EventContentDTO>,
    @SerialName("nextOffset")
    val nextOffset: ULong? = null,
)

@OptIn(ExperimentalSerializationApi::class)
class ConversationHistoryDepthSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Duration", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Duration) {
        encoder.encodeLong(value.inWholeSeconds)
    }

    override fun deserialize(decoder: Decoder): Duration {
        return decoder.decodeLong().seconds
    }
}
