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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import kotlin.time.DurationUnit
import kotlin.time.toDuration

public sealed interface StoredIncomingAssetMessage {
    public val senderUserId: UserId

    public data class RegularAsset(
        public val message: Message.Regular,
    ) : StoredIncomingAssetMessage {
        override val senderUserId: UserId = message.senderUserId
    }

    public data class RestrictedAsset(
        override val senderUserId: UserId,
    ) : StoredIncomingAssetMessage

    public data class UnsupportedRegular(
        override val senderUserId: UserId,
        public val messageId: String,
        public val conversationId: ConversationId,
        public val contentType: String,
    ) : StoredIncomingAssetMessage

    public data class System(
        override val senderUserId: UserId,
    ) : StoredIncomingAssetMessage
}

public fun interface IncomingAssetMessageLookup {
    public suspend fun getMessageById(
        conversationId: ConversationId,
        messageId: String,
    ): Either<StorageFailure, StoredIncomingAssetMessage>
}

public class IncomingAssetMessageLookupImpl public constructor(
    private val messageDAO: MessageDAO,
    private val mapper: IncomingAssetMessageMapper = IncomingAssetMessageMapper(),
) : IncomingAssetMessageLookup {
    override suspend fun getMessageById(
        conversationId: ConversationId,
        messageId: String,
    ): Either<StorageFailure, StoredIncomingAssetMessage> = wrapStorageRequest {
        messageDAO.getMessageById(messageId, conversationId.toDao())
    }.map(mapper::fromEntity)
}

public class IncomingAssetMessageMapper {
    public fun fromEntity(message: MessageEntity): StoredIncomingAssetMessage = when (message) {
        is MessageEntity.Regular -> when (val content = message.content) {
            is MessageEntityContent.Asset -> StoredIncomingAssetMessage.RegularAsset(
                message = message.toRegularAssetMessage(content),
            )

            is MessageEntityContent.RestrictedAsset -> StoredIncomingAssetMessage.RestrictedAsset(
                senderUserId = message.senderUserId.toModel(),
            )

            else -> StoredIncomingAssetMessage.UnsupportedRegular(
                senderUserId = message.senderUserId.toModel(),
                messageId = message.id,
                conversationId = message.conversationId.toModel(),
                contentType = content.toLogType(),
            )
        }

        is MessageEntity.System -> StoredIncomingAssetMessage.System(
            senderUserId = message.senderUserId.toModel(),
        )
    }

    private fun MessageEntity.Regular.toRegularAssetMessage(content: MessageEntityContent.Asset): Message.Regular =
        Message.Regular(
            id = id,
            content = MessageContent.Asset(content.toAssetContent()),
            conversationId = conversationId.toModel(),
            date = date,
            senderUserId = senderUserId.toModel(),
            senderClientId = ClientId(senderClientId),
            status = status.toModel(readCount),
            editStatus = editStatus.toModel(),
            expirationData = expireAfterMs?.let { expireAfterMs ->
                Message.ExpirationData(
                    expireAfter = expireAfterMs.toDuration(DurationUnit.MILLISECONDS),
                    selfDeletionStatus = selfDeletionEndDate?.let {
                        Message.ExpirationData.SelfDeletionStatus.Started(it)
                    } ?: Message.ExpirationData.SelfDeletionStatus.NotStarted,
                )
            },
            visibility = visibility.toModel(),
            reactions = Message.Reactions(
                reactions.reactions.mapValues { (_, reaction) ->
                    Message.ReactionData(reaction.count, reaction.isSelf)
                },
            ),
            senderUserName = senderName,
            isSelfMessage = isSelfMessage,
            expectsReadConfirmation = expectsReadConfirmation,
            deliveryStatus = deliveryStatus.toModel(),
        )

    private fun MessageEntityContent.Regular.toLogType(): String = when (this) {
        is MessageEntityContent.Asset -> "Asset"
        is MessageEntityContent.FailedDecryption -> "FailedDecryption"
        is MessageEntityContent.Knock -> "Knock"
        is MessageEntityContent.RestrictedAsset -> "RestrictedAsset"
        is MessageEntityContent.Text -> "Text"
        is MessageEntityContent.Composite -> "Composite"
        is MessageEntityContent.Location -> "Location"
        is MessageEntityContent.Unknown -> "Unknown"
        is MessageEntityContent.Multipart -> "Multipart"
    }
}
