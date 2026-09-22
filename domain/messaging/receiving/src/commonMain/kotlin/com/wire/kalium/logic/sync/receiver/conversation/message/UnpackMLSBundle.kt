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

package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.DecryptedMessageBundle
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.sync.KaliumSyncException
import kotlin.time.Instant

/** Decode a bundle; callers must complete required proposal work before accepting its handshake result. */
fun DecryptedMessageBundle.unpack(
    conversationId: ConversationId,
    messageInstant: Instant,
    protoContentMapper: ProtoContentMapper,
): MessageUnpackResult = when (this) {
    is DecryptedMessageBundle.Text -> unpackText(conversationId, messageInstant, protoContentMapper)
    is DecryptedMessageBundle.Commit, is DecryptedMessageBundle.Proposal -> MessageUnpackResult.HandshakeMessage
}

private fun DecryptedMessageBundle.Text.unpackText(
    conversationId: ConversationId,
    messageInstant: Instant,
    protoContentMapper: ProtoContentMapper,
): MessageUnpackResult.ApplicationMessage {
    val protoContent = protoContentMapper.decodeFromProtobuf(PlainMessageBlob(applicationMessage.message))
    if (protoContent !is ProtoContent.Readable) {
        throw KaliumSyncException("MLS message with external content", CoreFailure.Unknown(null))
    }
    return MessageUnpackResult.ApplicationMessage(
        conversationId = conversationId,
        instant = messageInstant,
        senderUserId = applicationMessage.senderID,
        senderClientId = applicationMessage.senderClientID,
        content = protoContent
    )
}
