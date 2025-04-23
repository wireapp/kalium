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
package com.wire.kalium.persistence.dao.message.attachment

import com.wire.kalium.persistence.dao.QualifiedIDEntity

data object MessageAttachmentMapper {
    @Suppress("LongParameterList", "UnusedParameter")
    fun toDao(
        assetId: String,
        assetVersionId: String,
        messageId: String,
        conversationId: QualifiedIDEntity,
        cellAsset: Boolean,
        localPath: String?,
        contentUrl: String?,
        previewUrl: String?,
        assetMimeType: String,
        assetSize: Long?,
        assetPath: String?,
        contentHash: String?,
        assetWidth: Int?,
        assetHeight: Int?,
        assetDurationMs: Long?,
        assetTransferStatus: String
    ): MessageAttachmentEntity =
        MessageAttachmentEntity(
            assetId = assetId,
            assetVersionId = assetVersionId,
            cellAsset = cellAsset,
            mimeType = assetMimeType,
            assetPath = assetPath,
            assetSize = assetSize,
            localPath = localPath,
            previewUrl = previewUrl,
            assetWidth = assetWidth,
            assetHeight = assetHeight,
            assetDuration = assetDurationMs,
            assetTransferStatus = assetTransferStatus,
            contentUrl = contentUrl,
            contentHash = contentHash,
        )
}
