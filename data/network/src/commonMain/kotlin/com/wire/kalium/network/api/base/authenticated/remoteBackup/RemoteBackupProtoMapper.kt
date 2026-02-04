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
package com.wire.kalium.network.api.base.authenticated.remoteBackup

import com.wire.kalium.network.api.authenticated.remoteBackup.MessageSyncAssetMetadataDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.MessageSyncFetchResponseDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.MessageSyncMentionDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.MessageSyncRequestDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.RemoteBackupEventDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.RemoteBAckupMessageContentDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.RemoteBackupPayloadDTO
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.protobuf.decodeFromByteArray
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.remote_backup.RemoteBackupAsset
import com.wire.kalium.protobuf.remote_backup.RemoteBackupAudioMetaData
import com.wire.kalium.protobuf.remote_backup.RemoteBackupDelete
import com.wire.kalium.protobuf.remote_backup.RemoteBackupEvent
import com.wire.kalium.protobuf.remote_backup.RemoteBackupGenericMetaData
import com.wire.kalium.protobuf.remote_backup.RemoteBackupImageMetaData
import com.wire.kalium.protobuf.remote_backup.RemoteBackupLastRead
import com.wire.kalium.protobuf.remote_backup.RemoteBackupLocation
import com.wire.kalium.protobuf.remote_backup.RemoteBackupMention
import com.wire.kalium.protobuf.remote_backup.RemoteBackupMessage
import com.wire.kalium.protobuf.remote_backup.RemoteBackupMessageContent
import com.wire.kalium.protobuf.remote_backup.RemoteBackupMessageSyncRequest
import com.wire.kalium.protobuf.remote_backup.RemoteBackupMessageSyncResponse
import com.wire.kalium.protobuf.remote_backup.RemoteBackupQualifiedId
import com.wire.kalium.protobuf.remote_backup.RemoteBackupText
import com.wire.kalium.protobuf.remote_backup.RemoteBackupUpsert
import com.wire.kalium.protobuf.remote_backup.RemoteBackupVideoMetaData

internal class RemoteBackupProtoMapper {

    fun encodeSyncRequest(request: MessageSyncRequestDTO): ByteArray =
        RemoteBackupMessageSyncRequest(
            userId = request.userId,
            events = request.events.map(::mapEventToProto)
        ).encodeToByteArray()

    fun decodeFetchResponse(bytes: ByteArray): MessageSyncFetchResponseDTO {
        val response = RemoteBackupMessageSyncResponse.decodeFromByteArray(bytes)
        return MessageSyncFetchResponseDTO(
            hasMore = response.hasMore,
            events = response.events.map(::mapEventFromProto),
            paginationToken = response.paginationToken
        )
    }

    fun encodeFetchResponse(response: MessageSyncFetchResponseDTO): ByteArray =
        RemoteBackupMessageSyncResponse(
            hasMore = response.hasMore,
            events = response.events.map(::mapEventToProto),
            paginationToken = response.paginationToken
        ).encodeToByteArray()

    private fun mapEventToProto(event: RemoteBackupEventDTO): RemoteBackupEvent =
        when (event) {
            is RemoteBackupEventDTO.Upsert -> RemoteBackupEvent(
                event = RemoteBackupEvent.Event.Upsert(
                    RemoteBackupUpsert(
                        messageId = event.messageId,
                        timestamp = event.timestamp,
                        payload = mapPayloadToProto(event.payload)
                    )
                )
            )
            is RemoteBackupEventDTO.Delete -> RemoteBackupEvent(
                event = RemoteBackupEvent.Event.Delete(
                    RemoteBackupDelete(
                        conversationId = event.conversationId,
                        messageId = event.messageId
                    )
                )
            )
            is RemoteBackupEventDTO.LastRead -> RemoteBackupEvent(
                event = RemoteBackupEvent.Event.LastRead(
                    RemoteBackupLastRead(
                        conversationId = event.conversationId,
                        lastRead = event.lastRead
                    )
                )
            )
        }

    private fun mapEventFromProto(event: RemoteBackupEvent): RemoteBackupEventDTO =
        when (val value = event.event) {
            is RemoteBackupEvent.Event.Upsert -> RemoteBackupEventDTO.Upsert(
                messageId = value.value.messageId,
                timestamp = value.value.timestamp,
                payload = mapPayloadFromProto(value.value.payload)
            )
            is RemoteBackupEvent.Event.Delete -> RemoteBackupEventDTO.Delete(
                conversationId = value.value.conversationId,
                messageId = value.value.messageId
            )
            is RemoteBackupEvent.Event.LastRead -> RemoteBackupEventDTO.LastRead(
                conversationId = value.value.conversationId,
                lastRead = value.value.lastRead
            )
            null -> error("RemoteBackupEvent is missing its event payload")
        }

    private fun mapPayloadToProto(payload: RemoteBackupPayloadDTO): RemoteBackupMessage =
        RemoteBackupMessage(
            id = payload.id,
            conversationId = payload.conversationId.toProto(),
            senderUserId = payload.senderUserId.toProto(),
            senderClientId = payload.senderClientId,
            creationDate = payload.creationDate,
            content = mapContentToProto(payload.content),
            lastEditTime = payload.lastEditTime
        )

    private fun mapPayloadFromProto(payload: RemoteBackupMessage): RemoteBackupPayloadDTO =
        RemoteBackupPayloadDTO(
            id = payload.id,
            conversationId = payload.conversationId.toDto(),
            senderUserId = payload.senderUserId.toDto(),
            senderClientId = payload.senderClientId,
            creationDate = payload.creationDate,
            content = mapContentFromProto(payload.content),
            lastEditTime = payload.lastEditTime
        )

    private fun mapContentToProto(content: RemoteBAckupMessageContentDTO): RemoteBackupMessageContent =
        when (content) {
            is RemoteBAckupMessageContentDTO.Text ->
                RemoteBackupMessageContent(
                    content = RemoteBackupMessageContent.Content.Text(
                        RemoteBackupText(
                            text = content.text,
                            mentions = content.mentions.map(::mapMentionToProto),
                            quotedMessageId = content.quotedMessageId
                        )
                    )
                )
            is RemoteBAckupMessageContentDTO.Asset ->
                RemoteBackupMessageContent(
                    content = RemoteBackupMessageContent.Content.Asset(
                        RemoteBackupAsset(
                            mimeType = content.mimeType,
                            size = content.size.toLong(),
                            name = content.name,
                            otrKey = content.otrKey,
                            sha256 = content.sha256,
                            assetId = content.assetId,
                            assetToken = content.assetToken,
                            assetDomain = content.assetDomain,
                            encryption = content.encryption,
                            metaData = content.metaData?.let(::mapAssetMetaToProto)
                        )
                    )
                )
            is RemoteBAckupMessageContentDTO.Location ->
                RemoteBackupMessageContent(
                    content = RemoteBackupMessageContent.Content.Location(
                        RemoteBackupLocation(
                            longitude = content.longitude,
                            latitude = content.latitude,
                            name = content.name,
                            zoom = content.zoom
                        )
                    )
                )
        }

    private fun mapContentFromProto(content: RemoteBackupMessageContent): RemoteBAckupMessageContentDTO =
        when (val payload = content.content) {
            is RemoteBackupMessageContent.Content.Text -> RemoteBAckupMessageContentDTO.Text(
                text = payload.value.text,
                mentions = payload.value.mentions.map(::mapMentionFromProto),
                quotedMessageId = payload.value.quotedMessageId
            )
            is RemoteBackupMessageContent.Content.Asset -> RemoteBAckupMessageContentDTO.Asset(
                mimeType = payload.value.mimeType,
                size = payload.value.size.toInt(),
                name = payload.value.name,
                otrKey = payload.value.otrKey,
                sha256 = payload.value.sha256,
                assetId = payload.value.assetId,
                assetToken = payload.value.assetToken,
                assetDomain = payload.value.assetDomain,
                encryption = payload.value.encryption,
                metaData = payload.value.metaData?.let(::mapAssetMetaFromProto)
            )
            is RemoteBackupMessageContent.Content.Location -> RemoteBAckupMessageContentDTO.Location(
                longitude = payload.value.longitude,
                latitude = payload.value.latitude,
                name = payload.value.name,
                zoom = payload.value.zoom
            )
            null -> error("RemoteBackupMessage is missing its content payload")
        }

    private fun mapMentionToProto(mention: MessageSyncMentionDTO): RemoteBackupMention =
        RemoteBackupMention(
            userId = mention.userId.toProto(),
            start = mention.start,
            length = mention.length
        )

    private fun mapMentionFromProto(mention: RemoteBackupMention): MessageSyncMentionDTO =
        MessageSyncMentionDTO(
            userId = mention.userId.toDto(),
            start = mention.start,
            length = mention.length
        )

    private fun mapAssetMetaToProto(metaData: MessageSyncAssetMetadataDTO): RemoteBackupAsset.MetaData<*> =
        when (metaData) {
            is MessageSyncAssetMetadataDTO.Image -> RemoteBackupAsset.MetaData.Image(
                RemoteBackupImageMetaData(
                    width = metaData.width,
                    height = metaData.height,
                    tag = metaData.tag
                )
            )
            is MessageSyncAssetMetadataDTO.Video -> RemoteBackupAsset.MetaData.Video(
                RemoteBackupVideoMetaData(
                    width = metaData.width,
                    height = metaData.height,
                    durationInMillis = metaData.duration
                )
            )
            is MessageSyncAssetMetadataDTO.Audio -> RemoteBackupAsset.MetaData.Audio(
                RemoteBackupAudioMetaData(
                    normalization = metaData.normalization,
                    durationInMillis = metaData.duration
                )
            )
            is MessageSyncAssetMetadataDTO.Generic -> RemoteBackupAsset.MetaData.Generic(
                RemoteBackupGenericMetaData(name = metaData.name)
            )
        }

    private fun mapAssetMetaFromProto(metaData: RemoteBackupAsset.MetaData<*>): MessageSyncAssetMetadataDTO =
        when (metaData) {
            is RemoteBackupAsset.MetaData.Image -> MessageSyncAssetMetadataDTO.Image(
                width = metaData.value.width,
                height = metaData.value.height,
                tag = metaData.value.tag
            )
            is RemoteBackupAsset.MetaData.Video -> MessageSyncAssetMetadataDTO.Video(
                width = metaData.value.width,
                height = metaData.value.height,
                duration = metaData.value.durationInMillis
            )
            is RemoteBackupAsset.MetaData.Audio -> MessageSyncAssetMetadataDTO.Audio(
                normalization = metaData.value.normalization,
                duration = metaData.value.durationInMillis
            )
            is RemoteBackupAsset.MetaData.Generic -> MessageSyncAssetMetadataDTO.Generic(
                name = metaData.value.name
            )
        }

    private fun QualifiedID.toProto(): RemoteBackupQualifiedId =
        RemoteBackupQualifiedId(
            id = value,
            domain = domain
        )

    private fun RemoteBackupQualifiedId.toDto(): QualifiedID =
        QualifiedID(
            value = id,
            domain = domain
        )
}
