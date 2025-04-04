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
package com.wire.kalium.persistence.dao.messageattachment

import com.wire.kalium.persistence.dao.QualifiedIDEntity

@Suppress("LongParameterList")
internal data object MessageAttachmentDraftMapper {
    fun toDao(
        attachmentId: String,
        versionId: String,
        conversationId: QualifiedIDEntity,
        mimeType: String,
        fileName: String,
        fileSize: Long,
        dataPath: String,
        nodePath: String,
        uploadStatus: String,
        assetWidth: Int?,
        assetHeight: Int?,
        assetDuration: Long?,
    ) = MessageAttachmentDraftEntity(
        uuid = attachmentId,
        versionId = versionId,
        conversationId = conversationId,
        mimeType = mimeType,
        fileName = fileName,
        fileSize = fileSize,
        dataPath = dataPath,
        nodePath = nodePath,
        uploadStatus = uploadStatus,
        assetWidth = assetWidth,
        assetHeight = assetHeight,
        assetDuration = assetDuration,
    )
}
