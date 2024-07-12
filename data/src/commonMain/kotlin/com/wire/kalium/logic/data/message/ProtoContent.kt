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
package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.Conversation

/**
 * The result of the [protobuf model](https://github.com/wireapp/generic-message-proto) parsing.
 *
 * It can be [ProtoContent] or [ExternalMessageInstructions].
 */
sealed interface ProtoContent {
    val messageUid: String

    /**
     * Regular message, with readable content that can be simply used.
     * @see [ExternalMessageInstructions]
     */
    data class Readable(
        override val messageUid: String,
        val messageContent: MessageContent.FromProto,
        val expectsReadConfirmation: Boolean,
        val legalHoldStatus: Conversation.LegalHoldStatus,
        val expiresAfterMillis: Long? = null
    ) : ProtoContent

    /**
     * The message doesn't contain an actual content,
     * but rather instructions on how to read an external message.
     *
     * This message content is used if original message results in large payload,
     * that would not be accepted by backend. Regular messages over [Proteus] are encrypted multiple times (per recipient)
     * and in case of multiple participants even quite small message can generate huge payload.
     * In that case we want to encrypt original message with symmetric encryption and only send a key to all participants.
     *
     * See Also: [External Proto Message](https://github.com/wireapp/generic-message-proto#external))
     * @see [GenericMessage.Content.External]
     */
    class ExternalMessageInstructions(
        override val messageUid: String,
        val otrKey: ByteArray,
        val sha256: ByteArray?,
        val encryptionAlgorithm: MessageEncryptionAlgorithm?
    ) : ProtoContent
}
