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

package com.wire.kalium.messagecontent

import com.wire.kalium.logic.data.message.ProtoContent

/**
 * One decoded GenericMessage together with its exact serialized representation.
 *
 * The byte array is owned by this value so callers can safely retain it for the foreground handoff.
 */
public class DecodedProtobufContent(
    serializedContent: ByteArray,
    public val content: ProtoContent,
    public val classification: Classification
) {
    private val serializedContentBytes: ByteArray = serializedContent.copyOf()

    public val serializedContent: ByteArray get() = serializedContentBytes.copyOf()

    public enum class Classification {
        APPLICATION,
        EXTERNAL_INSTRUCTIONS,
        UNSUPPORTED
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is DecodedProtobufContent &&
                serializedContentBytes.contentEquals(other.serializedContentBytes) &&
                content == other.content && classification == other.classification

    override fun hashCode(): Int =
        31 * (31 * serializedContentBytes.contentHashCode() + content.hashCode()) + classification.hashCode()
}

/** Narrow receive-only protobuf decoder. It performs no persistence or application side effects. */
public fun interface ProtobufMessageContentDecoder {
    public fun decode(serializedContent: ByteArray): DecodedProtobufContent
}
