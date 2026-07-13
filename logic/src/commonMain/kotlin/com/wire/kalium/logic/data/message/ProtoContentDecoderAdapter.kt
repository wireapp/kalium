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

package com.wire.kalium.logic.data.message

import com.wire.kalium.messaging.receiving.DecodedMessageContent
import com.wire.kalium.messaging.receiving.MessageContentDecoder

/** Bridges the receive-only cryptographic boundary to the shared protobuf codec. */
internal class ProtoContentDecoderAdapter(
    private val protoContentMapper: ProtoContentMapper
) : MessageContentDecoder<ProtoContent.Readable> {
    override fun decode(serializedContent: ByteArray): DecodedMessageContent<ProtoContent.Readable> =
        when (val protoContent = protoContentMapper.decodeFromProtobuf(PlainMessageBlob(serializedContent))) {
            is ProtoContent.Readable -> DecodedMessageContent.Application(protoContent)
            is ProtoContent.ExternalMessageInstructions -> DecodedMessageContent.ExternalInstructions(protoContent.otrKey)
        }
}
