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
package com.wire.kalium.logic.feature.backup.mapper

import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupMessageContent
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.data.BackupUser
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import kotlinx.datetime.Clock

private fun BackupQualifiedId.toQualifiedId() = QualifiedID(
    value = id,
    domain = domain
)

internal fun BackupUser.toUser() = OtherUser(
    id = id.toQualifiedId(),
    name = name,
    handle = handle,
    accentId = 0,
    teamId = null,
    previewPicture = null,
    completePicture = null,
    userType = UserType.NONE,
    availabilityStatus = UserAvailabilityStatus.NONE,
    supportedProtocols = null,
    botService = null,
    deleted = true,
    defederated = false,
    isProteusVerified = false,
)

internal fun BackupConversation.toConversation() = Conversation(
    id = id.toQualifiedId(),
    name = name,
    type = if (name.isBlank()) {
        Conversation.Type.OneOnOne
    } else {
        Conversation.Type.Group.Regular
    },
    teamId = null,
    protocol = Conversation.ProtocolInfo.Proteus,
    mutedStatus = MutedConversationStatus.AllMuted,
    removedBy = null,
    lastNotificationDate = null,
    lastModifiedDate = null,
    lastReadDate = Clock.System.now(),
    access = emptyList(),
    accessRole = emptyList(),
    creatorId = null,
    receiptMode = Conversation.ReceiptMode.DISABLED,
    messageTimer = null,
    userMessageTimer = null,
    archived = false,
    archivedDateTime = null,
    mlsVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
    proteusVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
    legalHoldStatus = Conversation.LegalHoldStatus.UNKNOWN,
)

internal fun BackupMessage.toMessage(selfUserId: UserId): Message.Standalone {

    val isSelfMessage = senderUserId.toQualifiedId() == selfUserId

    return Message.Regular(
        id = id,
        content = content.toMessageContent(),
        conversationId = conversationId.toQualifiedId(),
        date = creationDate.instant,
        senderUserId = senderUserId.toQualifiedId(),
        status = if (isSelfMessage) {
            Message.Status.Sent
        } else {
            Message.Status.Read(1)
        },
        isSelfMessage = isSelfMessage,
        senderClientId = ClientId(senderClientId),
        editStatus = Message.EditStatus.NotEdited
    )
}

private fun BackupMessageContent.toMessageContent() =
    when (this) {
        is BackupMessageContent.Text -> MessageContent.Text(
            value = text,
        )

        is BackupMessageContent.Location -> MessageContent.Location(
            latitude = latitude,
            longitude = longitude,
        )

        is BackupMessageContent.Asset -> MessageContent.Asset(
            value = AssetContent(
                sizeInBytes = size.toLong(),
                name = name,
                mimeType = mimeType,
                metadata = metaData?.toMetadata(),
                remoteData = AssetContent.RemoteData(
                    otrKey = otrKey,
                    sha256 = sha256,
                    assetId = assetId,
                    assetToken = assetToken,
                    assetDomain = assetDomain,
                    encryptionAlgorithm = encryption?.toAlgorithm(),
                ),
            )
        )
    }

private fun BackupMessageContent.Asset.AssetMetadata.toMetadata() = when (this) {
    is BackupMessageContent.Asset.AssetMetadata.Image -> AssetContent.AssetMetadata.Image(
        width = width,
        height = height,
    )

    is BackupMessageContent.Asset.AssetMetadata.Audio -> AssetContent.AssetMetadata.Audio(
        durationMs = duration,
        normalizedLoudness = normalization,
    )

    is BackupMessageContent.Asset.AssetMetadata.Video -> AssetContent.AssetMetadata.Video(
        width = width,
        height = height,
        durationMs = duration,
    )

    else -> null
}

private fun BackupMessageContent.Asset.EncryptionAlgorithm.toAlgorithm() = when (this) {
    BackupMessageContent.Asset.EncryptionAlgorithm.AES_GCM -> MessageEncryptionAlgorithm.AES_GCM
    BackupMessageContent.Asset.EncryptionAlgorithm.AES_CBC -> MessageEncryptionAlgorithm.AES_CBC
}
