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
package com.wire.backup.ingest

import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupData
import com.wire.backup.data.BackupDateTime
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupMessageContent
import com.wire.backup.data.BackupMessageContent.Asset.EncryptionAlgorithm
import com.wire.backup.data.BackupMetadata
import com.wire.backup.data.BackupUser
import com.wire.backup.data.toLongMilliseconds
import com.wire.backup.data.toModel
import com.wire.backup.data.toProtoModel
import com.wire.kalium.protobuf.backup.ExportUser
import com.wire.kalium.protobuf.backup.ExportedAsset
import com.wire.kalium.protobuf.backup.ExportedAudioMetaData
import com.wire.kalium.protobuf.backup.ExportedConversation
import com.wire.kalium.protobuf.backup.ExportedEncryptionAlgorithm
import com.wire.kalium.protobuf.backup.ExportedGenericMetaData
import com.wire.kalium.protobuf.backup.ExportedImageMetaData
import com.wire.kalium.protobuf.backup.ExportedLocation
import com.wire.kalium.protobuf.backup.ExportedMessage
import com.wire.kalium.protobuf.backup.ExportedMessage.Content
import com.wire.kalium.protobuf.backup.ExportedText
import com.wire.kalium.protobuf.backup.ExportedVideoMetaData
import pbandk.ByteArr
import com.wire.kalium.protobuf.backup.BackupData as ProtoBackupData

internal class MPBackupMapper {

    fun mapUserToProtobuf(it: BackupUser) = ExportUser(
        id = it.id.toProtoModel(),
        name = it.name,
        handle = it.handle
    )

    @Suppress("LongMethod")
    fun mapMessageToProtobuf(it: BackupMessage): ExportedMessage {
        return ExportedMessage(
            id = it.id.lowercase(),
            timeIso = it.creationDate.toLongMilliseconds(),
            senderUserId = it.senderUserId.toProtoModel(),
            senderClientId = it.senderClientId,
            conversationId = it.conversationId.toProtoModel(),
            content = when (val content = it.content) {
                is BackupMessageContent.Asset -> {
                    Content.Asset(
                        ExportedAsset(
                            content.mimeType,
                            content.size.toLong(),
                            content.name,
                            ByteArr(content.otrKey),
                            ByteArr(content.sha256),
                            content.assetId,
                            content.assetToken,
                            content.assetDomain,
                            when (content.encryption) {
                                EncryptionAlgorithm.AES_GCM -> ExportedEncryptionAlgorithm.BACKUP_AES_GCM
                                EncryptionAlgorithm.AES_CBC -> ExportedEncryptionAlgorithm.BACKUP_AES_CBC
                                null -> null
                            },
                            content.metaData?.let {
                                when (it) {
                                    is BackupMessageContent.Asset.AssetMetadata.Audio ->
                                        ExportedAsset.MetaData.Audio(
                                            ExportedAudioMetaData(
                                                it.duration,
                                                it.normalization?.let { ByteArr(it) }
                                            )
                                        )

                                    is BackupMessageContent.Asset.AssetMetadata.Image ->
                                        ExportedAsset.MetaData.Image(
                                            ExportedImageMetaData(
                                                it.width,
                                                it.height,
                                                it.tag
                                            )
                                        )

                                    is BackupMessageContent.Asset.AssetMetadata.Video ->
                                        ExportedAsset.MetaData.Video(
                                            ExportedVideoMetaData(
                                                it.width,
                                                it.height,
                                                it.duration
                                            )
                                        )

                                    is BackupMessageContent.Asset.AssetMetadata.Generic ->
                                        ExportedAsset.MetaData.Generic(ExportedGenericMetaData(it.name))
                                }
                            }
                        )
                    )
                }

                is BackupMessageContent.Text ->
                    Content.Text(ExportedText(content.text))

                is BackupMessageContent.Location -> Content.Location(
                    ExportedLocation(
                        content.longitude,
                        content.latitude,
                        content.name,
                        content.zoom
                    )
                )
            },
            webPk = it.webPrimaryKey?.toLong(),
            lastEditTime = it.lastEditTime?.toLongMilliseconds(),
        )
    }

    fun mapConversationToProtobuf(it: BackupConversation) = ExportedConversation(
        id = it.id.toProtoModel(),
        name = it.name,
        lastModifiedTime = it.lastModifiedTime?.toLongMilliseconds(),
    )

    fun fromProtoToBackupModel(
        protobufData: ProtoBackupData
    ): BackupData = protobufData.run {
        BackupData(
            BackupMetadata(
                info.version,
                info.userId.toModel(),
                BackupDateTime(info.creationTime),
                info.clientId
            ),
            users.map { user ->
                fromUserProtoToBackupModel(user)
            }.toTypedArray(),
            conversations.map { conversation ->
                fromConversationProtoToBackupModel(conversation)
            }.toTypedArray(),
            messages.map { message ->
                fromMessageProtoToBackupModel(message)
            }.toTypedArray()
        )
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun fromMessageProtoToBackupModel(message: ExportedMessage): BackupMessage {
        val content = when (val protoContent = message.content) {
            is Content.Text -> {
                BackupMessageContent.Text(protoContent.value.content)
            }

            is Content.Asset -> BackupMessageContent.Asset(
                protoContent.value.mimetype,
                protoContent.value.size.toInt(),
                protoContent.value.name,
                protoContent.value.otrKey.array,
                protoContent.value.sha256.array,
                protoContent.value.assetId,
                protoContent.value.assetToken,
                protoContent.value.assetDomain,
                protoContent.value.encryption?.let {
                    when (it) {
                        ExportedEncryptionAlgorithm.BACKUP_AES_CBC -> EncryptionAlgorithm.AES_CBC
                        ExportedEncryptionAlgorithm.BACKUP_AES_GCM -> EncryptionAlgorithm.AES_GCM
                        is ExportedEncryptionAlgorithm.UNRECOGNIZED -> null
                    }
                },
                protoContent.value.metaData?.let {
                    when (it) {
                        is ExportedAsset.MetaData.Audio -> BackupMessageContent.Asset.AssetMetadata.Audio(
                            it.value.normalizedLoudness?.array,
                            it.value.durationInMillis
                        )

                        is ExportedAsset.MetaData.Image -> BackupMessageContent.Asset.AssetMetadata.Image(
                            it.value.width,
                            it.value.height,
                            it.value.tag
                        )

                        is ExportedAsset.MetaData.Video -> BackupMessageContent.Asset.AssetMetadata.Video(
                            it.value.width,
                            it.value.height,
                            it.value.durationInMillis
                        )

                        is ExportedAsset.MetaData.Generic -> BackupMessageContent.Asset.AssetMetadata.Generic(
                            it.value.name
                        )
                    }
                }
            )

            is Content.Location -> BackupMessageContent.Location(
                protoContent.value.longitude,
                protoContent.value.latitude,
                protoContent.value.name,
                protoContent.value.zoom
            )

            null -> throw IllegalArgumentException("Message content cannot be null!")
        }
        return BackupMessage(
            id = message.id,
            conversationId = message.conversationId.toModel(),
            senderUserId = message.senderUserId.toModel(),
            senderClientId = message.senderClientId,
            creationDate = BackupDateTime(message.timeIso),
            content = content,
            webPrimaryKey = message.webPk?.toInt(),
            lastEditTime = message.lastEditTime?.let { BackupDateTime(it) },
        )
    }

    private fun fromConversationProtoToBackupModel(conversation: ExportedConversation) =
        BackupConversation(
            id = conversation.id.toModel(),
            name = conversation.name,
            lastModifiedTime = conversation.lastModifiedTime?.let { BackupDateTime(it) }
        )

    private fun fromUserProtoToBackupModel(user: ExportUser) =
        BackupUser(user.id.toModel(), user.name, user.handle)
}
