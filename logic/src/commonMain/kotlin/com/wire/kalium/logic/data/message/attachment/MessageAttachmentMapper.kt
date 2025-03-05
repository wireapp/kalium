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
package com.wire.kalium.logic.data.message.attachment

import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.asset.AttachmentType
import com.wire.kalium.logic.data.asset.toModel
import com.wire.kalium.logic.data.asset.toProto
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.logic.data.message.MessageAttachment
import com.wire.kalium.logic.data.message.durationMs
import com.wire.kalium.logic.data.message.height
import com.wire.kalium.logic.data.message.width
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity
import com.wire.kalium.protobuf.messages.Attachment
import com.wire.kalium.protobuf.messages.CellAsset

interface MessageAttachmentMapper {
    fun fromDaoToModel(attachment: MessageAttachmentEntity): MessageAttachment
    fun fromModelToDao(attachment: MessageAttachment): MessageAttachmentEntity
    fun fromProtoToModel(attachment: Attachment): MessageAttachment?
    fun fromModelToProto(attachment: MessageAttachment): Attachment
}

class MessageAttachmentMapperImpl : MessageAttachmentMapper {
    override fun fromDaoToModel(attachment: MessageAttachmentEntity): MessageAttachment =
        if (attachment.cellAsset) {
            CellAssetContent(
                id = attachment.assetId,
                versionId = attachment.assetVersionId,
                mimeType = attachment.mimeType,
                assetPath = attachment.assetPath,
                assetSize = attachment.assetSize,
                previewUrl = attachment.previewUrl?.takeIf { it.isNotEmpty() },
                localPath = attachment.localPath?.takeIf { it.isNotEmpty() },
                metadata = attachment.metadata(),
                transferStatus = AssetTransferStatus.valueOf(attachment.assetTransferStatus)
            )
        } else {
            TODO()
        }

    override fun fromModelToDao(attachment: MessageAttachment): MessageAttachmentEntity {
        return when (attachment) {
            is CellAssetContent -> MessageAttachmentEntity(
                assetId = attachment.id,
                assetVersionId = attachment.versionId,
                cellAsset = true,
                mimeType = attachment.mimeType,
                localPath = attachment.localPath,
                previewUrl = null,
                assetPath = attachment.assetPath,
                assetSize = attachment.assetSize,
                assetWidth = attachment.metadata?.width(),
                assetHeight = attachment.metadata?.height(),
                assetDuration = attachment.metadata?.durationMs(),
                assetTransferStatus = attachment.transferStatus.name,
            )

            is AssetContent -> TODO()
        }
    }

    override fun fromProtoToModel(attachment: Attachment): MessageAttachment? {

        val cellAsset = attachment.cellAsset?.let { asset ->
            CellAssetContent(
                id = asset.uuid,
                versionId = "",
                mimeType = asset.contentType,
                assetPath = asset.initialName,
                assetSize = asset.initialSize,
                metadata = asset.initialMetaData?.toModel(),
                transferStatus = AssetTransferStatus.NOT_DOWNLOADED,
            )
        }

        return cellAsset
    }

    override fun fromModelToProto(attachment: MessageAttachment): Attachment {
        return when (attachment) {
            is CellAssetContent -> Attachment(
                content = Attachment.Content.CellAsset(
                    CellAsset(
                        uuid = attachment.id,
                        contentType = attachment.mimeType,
                        initialMetaData = attachment.metadata?.toProto(),
                    )
                )
            )

            is AssetContent -> TODO()
        }
    }
}

fun MessageAttachmentEntity.toModel() =
    if (cellAsset) {
        CellAssetContent(
            id = assetId,
            versionId = assetVersionId,
            mimeType = mimeType,
            assetPath = assetPath,
            assetSize = assetSize,
            previewUrl = previewUrl?.takeIf { it.isNotEmpty() },
            localPath = localPath?.takeIf { it.isNotEmpty() },
            metadata = metadata(),
            transferStatus = AssetTransferStatus.valueOf(assetTransferStatus)
        )
    } else {
        TODO()
    }

@Suppress("CyclomaticComplexMethod")
fun MessageAttachmentEntity.metadata(): AssetContent.AssetMetadata? {

    val type = AttachmentType.fromMimeTypeString(mimeType)

    return when (type) {
        AttachmentType.IMAGE -> AssetContent.AssetMetadata.Image(
            width = assetWidth ?: 0,
            height = assetHeight ?: 0
        )

        AttachmentType.AUDIO -> AssetContent.AssetMetadata.Audio(
            durationMs = assetDuration ?: 0,
            normalizedLoudness = null,
        )

        AttachmentType.VIDEO -> AssetContent.AssetMetadata.Video(
            width = assetWidth ?: 0,
            height = assetHeight ?: 0,
            durationMs = assetDuration ?: 0
        )

        AttachmentType.GENERIC_FILE -> if (assetWidth != null && assetHeight != null) {
            AssetContent.AssetMetadata.Image(
                width = assetWidth ?: 0,
                height = assetHeight ?: 0,
            )
        } else {
            null
        }
    }
}
