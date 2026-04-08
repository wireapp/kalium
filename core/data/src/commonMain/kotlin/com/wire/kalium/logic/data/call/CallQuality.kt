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
@file:OptIn(ExperimentalSerializationApi::class)

package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.id.ConversationId
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
import kotlin.math.max

class CallQualityDataProfile(
    data: Map<ConversationId, CallQualityData> = emptyMap()
) : Map<ConversationId, CallQualityData> by data {
    fun plus(conversationId: ConversationId, data: CallQualityData) = CallQualityDataProfile(this + (conversationId to data))
}

@Serializable
data class CallQualityData(
    @SerialName("quality") val quality: Quality = Quality.UNKNOWN,
    @SerialName("peer") val peer: Peer = Peer.UNKNOWN,
    @SerialName("connection") val connection: Connection = Connection(),
    @SerialName("rtt") val ping: Int = -1, // milliseconds of round-trip time
    @SerialName("loss") val packetLoss: PacketLoss = PacketLoss(), // percentage of packets lost
    @SerialName("jitter") val jitter: Jitter = Jitter(), // milliseconds of variation in packet delay
) {
    @Serializable(with = QualityAsStringSerializer::class)
    enum class Quality {
        UNKNOWN, NORMAL, MEDIUM, POOR, NETWORK_PROBLEM, RECONNECTING;

        val isLowQuality: Boolean get() = this >= POOR
    }

    @Serializable(with = PeerAsStringSerializer::class)
    enum class Peer {
        UNKNOWN, USER, SERVER
    }

    @Serializable
    data class Connection(
        @SerialName("protocol") val protocol: Protocol = Protocol.UNKNOWN,
        @SerialName("candidate") val candidate: Candidate = Candidate.UNKNOWN,
    ) {
        @Serializable(with = ProtocolAsStringSerializer::class)
        enum class Protocol {
            UNKNOWN, UDP, TCP
        }

        @Serializable(with = CandidateAsStringSerializer::class)
        enum class Candidate {
            UNKNOWN, HOST, SFRLX, PRFLX, RELAY
        }
    }

    @Serializable
    data class UpDown(
        @SerialName("tx") val up: Int = -1,
        @SerialName("rx") val down: Int = -1,
    ) {
        val max = max(up, down)
    }

    typealias PacketLoss = UpDown
    typealias AudioJitter = UpDown
    typealias VideoJitter = UpDown

    @Serializable
    data class Jitter(
        @SerialName("audio") val audio: AudioJitter = AudioJitter(),
        @SerialName("video") val video: VideoJitter = VideoJitter(),
    ) {
        val max = max(audio.max, video.max)
    }
}

private data object QualityAsStringSerializer : KSerializer<CallQualityData.Quality> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Quality", PrimitiveKind.INT).nullable
    override fun serialize(encoder: Encoder, value: CallQualityData.Quality) {
        encoder.encodeInt(value.ordinal)
    }
    override fun deserialize(decoder: Decoder): CallQualityData.Quality {
        val value = if (decoder.decodeNotNullMark()) decoder.decodeInt() else 0
        return CallQualityData.Quality.entries.getOrNull(value) ?: CallQualityData.Quality.UNKNOWN
    }
}

private data object PeerAsStringSerializer : KSerializer<CallQualityData.Peer> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Peer", PrimitiveKind.STRING).nullable
    override fun serialize(encoder: Encoder, value: CallQualityData.Peer) {
        encoder.encodeString(value.name.capitalize())
    }
    override fun deserialize(decoder: Decoder): CallQualityData.Peer {
        val value = if (decoder.decodeNotNullMark()) decoder.decodeString() else ""
        return CallQualityData.Peer.entries
            .firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CallQualityData.Peer.UNKNOWN
    }
}

private data object ProtocolAsStringSerializer : KSerializer<CallQualityData.Connection.Protocol> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Protocol", PrimitiveKind.STRING).nullable
    override fun serialize(encoder: Encoder, value: CallQualityData.Connection.Protocol) {
        encoder.encodeString(value.name.capitalize())
    }
    override fun deserialize(decoder: Decoder): CallQualityData.Connection.Protocol {
        val value = if (decoder.decodeNotNullMark()) decoder.decodeString() else ""
        return CallQualityData.Connection.Protocol.entries
            .firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CallQualityData.Connection.Protocol.UNKNOWN
    }
}

private data object CandidateAsStringSerializer : KSerializer<CallQualityData.Connection.Candidate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Candidate", PrimitiveKind.STRING).nullable
    override fun serialize(encoder: Encoder, value: CallQualityData.Connection.Candidate) {
        encoder.encodeString(value.name.capitalize())
    }
    override fun deserialize(decoder: Decoder): CallQualityData.Connection.Candidate {
        val value = if (decoder.decodeNotNullMark()) decoder.decodeString() else ""
        return CallQualityData.Connection.Candidate.entries
            .firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CallQualityData.Connection.Candidate.UNKNOWN
    }
}

private fun String.capitalize() = lowercase().replaceFirstChar { it.titlecase() }
