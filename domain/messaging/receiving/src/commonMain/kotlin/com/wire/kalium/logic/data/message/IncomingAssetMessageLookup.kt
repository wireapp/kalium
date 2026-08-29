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
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Audio
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Image
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Video
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.DeliveryStatusEntity
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.util.InternalKaliumApi
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@InternalKaliumApi
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

@InternalKaliumApi
public fun interface IncomingAssetMessageLookup {
    public suspend fun getMessageById(
        conversationId: ConversationId,
        messageId: String,
    ): Either<StorageFailure, StoredIncomingAssetMessage>
}

@InternalKaliumApi
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

@InternalKaliumApi
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

    private fun MessageEntityContent.Asset.toAssetContent(): AssetContent = AssetContent(
        sizeInBytes = assetSizeInBytes,
        name = assetName,
        mimeType = assetMimeType,
        metadata = when {
            assetMimeType.contains("image/") -> Image(
                width = assetWidth ?: 0,
                height = assetHeight ?: 0,
            )

            assetMimeType.contains("video/") -> Video(
                width = assetWidth,
                height = assetHeight,
                durationMs = assetDurationMs,
            )

            assetMimeType.contains("audio/") -> Audio(
                durationMs = assetDurationMs,
                normalizedLoudness = assetNormalizedLoudness,
            )

            else -> null
        },
        remoteData = AssetContent.RemoteData(
            otrKey = assetOtrKey,
            sha256 = assetSha256Key,
            assetId = assetId,
            assetToken = assetToken,
            assetDomain = assetDomain,
            encryptionAlgorithm = when {
                assetEncryptionAlgorithm?.contains("CBC") == true -> MessageEncryptionAlgorithm.AES_CBC
                assetEncryptionAlgorithm?.contains("GCM") == true -> MessageEncryptionAlgorithm.AES_GCM
                else -> MessageEncryptionAlgorithm.AES_CBC
            },
        ),
    )

    private fun MessageEntity.Status.toModel(readCount: Long): Message.Status = when (this) {
        MessageEntity.Status.PENDING -> Message.Status.Pending
        MessageEntity.Status.SENT -> Message.Status.Sent
        MessageEntity.Status.DELIVERED -> Message.Status.Delivered
        MessageEntity.Status.READ -> Message.Status.Read(readCount)
        MessageEntity.Status.FAILED -> Message.Status.Failed
        MessageEntity.Status.FAILED_REMOTELY -> Message.Status.FailedRemotely
    }

    private fun MessageEntity.Visibility.toModel(): Message.Visibility = when (this) {
        MessageEntity.Visibility.VISIBLE -> Message.Visibility.VISIBLE
        MessageEntity.Visibility.DELETED -> Message.Visibility.DELETED
        MessageEntity.Visibility.HIDDEN -> Message.Visibility.HIDDEN
    }

    private fun DeliveryStatusEntity.toModel(): DeliveryStatus = when (this) {
        DeliveryStatusEntity.CompleteDelivery -> DeliveryStatus.CompleteDelivery
        is DeliveryStatusEntity.PartialDelivery -> DeliveryStatus.PartialDelivery(
            recipientsFailedWithNoClients = recipientsFailedWithNoClients.map { it.toModel() },
            recipientsFailedDelivery = recipientsFailedDelivery.map { it.toModel() },
        )
    }

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
