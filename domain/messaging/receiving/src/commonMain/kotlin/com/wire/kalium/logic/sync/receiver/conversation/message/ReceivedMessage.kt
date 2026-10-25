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
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.toMessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import kotlin.time.Instant
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/** The app starts expiration in its executor; NSE can persist a start time for that executor to resume. */
@Suppress("CyclomaticComplexMethod") // The exhaustive visibility `when` makes every new content type choose its visibility.
fun MessageUnpackResult.ApplicationMessage.toRegularMessage(
    selfUserId: UserId,
    selfDeletionStartedAt: Instant? = null,
): Message.Regular {
    val protoContent = requireNotNull(content.messageContent as? MessageContent.Regular)
    val visibility = when (protoContent) {
        is MessageContent.Unknown -> if (protoContent.hidden) Message.Visibility.HIDDEN else Message.Visibility.VISIBLE
        is MessageContent.Text -> Message.Visibility.VISIBLE
        is MessageContent.Asset -> Message.Visibility.VISIBLE
        is MessageContent.Knock -> Message.Visibility.VISIBLE
        is MessageContent.RestrictedAsset -> Message.Visibility.VISIBLE
        is MessageContent.FailedDecryption -> Message.Visibility.VISIBLE
        is MessageContent.Composite -> Message.Visibility.VISIBLE
        is MessageContent.Poll -> Message.Visibility.VISIBLE
        is MessageContent.Location -> Message.Visibility.VISIBLE
        is MessageContent.Multipart -> Message.Visibility.VISIBLE
    }
    return Message.Regular(
        id = content.messageUid,
        content = protoContent,
        conversationId = conversationId,
        date = instant,
        senderUserId = senderUserId,
        senderClientId = senderClientId,
        status = Message.Status.Sent,
        editStatus = Message.EditStatus.NotEdited,
        visibility = visibility,
        expectsReadConfirmation = content.expectsReadConfirmation,
        isSelfMessage = senderUserId == selfUserId,
        targeted = targeted,
        transient = transient,
        expirationData = content.expiresAfterMillis?.let {
            Message.ExpirationData(
                expireAfter = it.toDuration(DurationUnit.MILLISECONDS),
                selfDeletionStatus = if (senderUserId == selfUserId && selfDeletionStartedAt != null) {
                    Message.ExpirationData.SelfDeletionStatus.Started(selfDeletionStartedAt + it.toDuration(DurationUnit.MILLISECONDS))
                } else Message.ExpirationData.SelfDeletionStatus.NotStarted
            )
        }
    )
}

fun MessageUnpackResult.ApplicationMessage.toSignalingMessage(selfUserId: UserId): Message.Signaling = Message.Signaling(
    id = content.messageUid,
    content = requireNotNull(content.messageContent as? MessageContent.Signaling),
    conversationId = conversationId,
    date = instant,
    senderUserId = senderUserId,
    senderClientId = senderClientId,
    status = Message.Status.Sent,
    isSelfMessage = senderUserId == selfUserId,
    expirationData = content.expiresAfterMillis?.let { Message.ExpirationData(it.toDuration(DurationUnit.MILLISECONDS)) },
    targeted = targeted,
    transient = transient,
)

/** Missing or mismatching quotes are unverified; a failed storage read remains a failure. */
suspend fun verifyMessageQuote(
    quotedReference: MessageContent.QuoteReference,
    conversationId: ConversationId,
    messageDAO: MessageDAO,
    selfUserId: UserId,
    messageEncoder: MessageContentEncoder = MessageContentEncoder(),
): Either<CoreFailure, MessageContent.QuoteReference> {
    val hash = quotedReference.quotedMessageSha256
        ?: return Either.Right(quotedReference.copy(isVerified = false))
    return wrapStorageRequest {
        val original = messageDAO.getMessageById(quotedReference.quotedMessageId, conversationId.toDao())
        val originalHash = (original as? MessageEntity.Regular)?.let {
            messageEncoder.encodeMessageContent(
                it.date,
                it.content.toMessageContent(it.visibility == MessageEntity.Visibility.HIDDEN, selfUserId)
            )
        }
        quotedReference.copy(isVerified = hash.contentEquals(originalHash?.sha256Digest))
    }
}

suspend fun prepareReceivedTextMessage(
    message: Message.Regular,
    messageDAO: MessageDAO,
    selfUserId: UserId,
    messageEncoder: MessageContentEncoder = MessageContentEncoder(),
): Either<CoreFailure, Message.Regular> {
    val text = requireNotNull(message.content as? MessageContent.Text)
    val quote = text.quotedMessageReference ?: return Either.Right(message)
    return verifyMessageQuote(quote, message.conversationId, messageDAO, selfUserId, messageEncoder).map {
        message.copy(content = text.copy(quotedMessageReference = it))
    }
}
