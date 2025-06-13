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
import com.wire.backup.data.BackupDateTime
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupMessageContent
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.data.BackupUser
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.user.OtherUser

internal fun QualifiedID.toBackupQualifiedId() = BackupQualifiedId(
    id = value,
    domain = domain
)

internal fun OtherUser.toBackupUser() = BackupUser(
    id = id.toBackupQualifiedId(),
    name = name ?: "",
    handle = handle ?: "",
)

internal fun Conversation.toBackupConversation() = BackupConversation(
    id = id.toBackupQualifiedId(),
    name = name ?: "",
)

internal fun Message.toBackupMessage() =
    backupMessageContent()?.let { content ->
        BackupMessage(
            id = id,
            conversationId = conversationId.toBackupQualifiedId(),
            senderUserId = senderUserId.toBackupQualifiedId(),
            senderClientId = sender?.id.toString(),
            creationDate = BackupDateTime(date.toEpochMilliseconds()),
            content = content,
        )
    }

private fun Message.backupMessageContent(): BackupMessageContent? = when (this) {
    is Message.Regular -> when (content) {
        is MessageContent.Text -> BackupMessageContent.Text((content as MessageContent.Text).value)
        is MessageContent.Asset -> with((content as MessageContent.Asset).value) {
            BackupMessageContent.Asset(
                mimeType = mimeType,
                size = sizeInBytes.toInt(),
                name = name,
                otrKey = remoteData.otrKey,
                sha256 = remoteData.sha256,
                assetId = remoteData.assetId,
                assetToken = remoteData.assetToken,
                assetDomain = remoteData.assetDomain,
                encryption = remoteData.encryptionAlgorithm?.toBackupModel(),
                metaData = metadata?.toBackupModel(),
            )
        }

        is MessageContent.Location -> with(content as MessageContent.Location) {
            BackupMessageContent.Location(
                name = name,
                latitude = latitude,
                longitude = longitude,
                zoom = zoom,
            )
        }

        else ->
            // Not supported content type
            null
    }

    is Message.Signaling -> null
    is Message.System -> null
}

private fun MessageEncryptionAlgorithm.toBackupModel() = when (this) {
    MessageEncryptionAlgorithm.AES_CBC -> BackupMessageContent.Asset.EncryptionAlgorithm.AES_CBC
    MessageEncryptionAlgorithm.AES_GCM -> BackupMessageContent.Asset.EncryptionAlgorithm.AES_GCM
}

private fun AssetContent.AssetMetadata.toBackupModel() = when (this) {
    is AssetContent.AssetMetadata.Image -> BackupMessageContent.Asset.AssetMetadata.Image(
        width = width,
        height = height,
        tag = null,
    )

    is AssetContent.AssetMetadata.Video -> BackupMessageContent.Asset.AssetMetadata.Video(
        width = width,
        height = height,
        duration = durationMs,
    )

    is AssetContent.AssetMetadata.Audio -> BackupMessageContent.Asset.AssetMetadata.Audio(
        normalization = normalizedLoudness,
        duration = durationMs,
    )
}
