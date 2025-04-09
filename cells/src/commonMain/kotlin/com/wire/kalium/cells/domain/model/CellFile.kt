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
package com.wire.kalium.cells.domain.model

import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata

/**
 * Represents file uploaded to wire cell.
 * Contains information from Cell server + local data (local path, owner name, conversation name)
  */
public data class CellFile(
    val uuid: String,
    val versionId: String,
    val fileName: String,
    val mimeType: String,
    val remotePath: String?,
    val localPath: String? = null,
    val assetSize: Long?,
    val contentHash: String? = null,
    val contentUrl: String? = null,
    val previewUrl: String? = null,
    val metadata: AssetMetadata? = null,
    val userName: String? = null,
    val conversationName: String? = null,
    val publicLinkId: String? = null,
    val lastModified: Long? = null,
)

@Suppress("MagicNumber")
internal fun CellNode.toFileModel() = CellFile(
    uuid = uuid,
    versionId = versionId,
    fileName = path.substringAfterLast("/"),
    mimeType = mimeType ?: "",
    remotePath = path,
    contentHash = contentHash,
    contentUrl = contentUrl,
    previewUrl = previews.maxByOrNull { it.dimension }?.url,
    assetSize = size,
    publicLinkId = publicLinkId,
    lastModified = modified?.let { it * 1000 },
)
