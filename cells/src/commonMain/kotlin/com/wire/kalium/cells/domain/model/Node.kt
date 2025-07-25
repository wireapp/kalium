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
 * Represents file or folder.
 */
public sealed class Node {
    public abstract val name: String?
    public abstract val uuid: String
    public abstract val userName: String?
    public abstract val conversationName: String?
    public abstract val modifiedTime: Long?
    public abstract val remotePath: String?
    public abstract val size: Long?
    public abstract val tags: List<String>

    public data class Folder(
        override val name: String?,
        override val uuid: String,
        override val userName: String? = null,
        override val conversationName: String? = null,
        override val modifiedTime: Long?,
        override val remotePath: String?,
        override val size: Long?,
        override val tags: List<String> = emptyList(),
    ) : Node()

    /**
     * Represents file uploaded to wire cell.
     * Contains information from Cell server + local data (local path, owner name, conversation name)
     */
    public data class File(
        override val name: String?,
        override val uuid: String,
        override val userName: String? = null,
        override val conversationName: String? = null,
        override val modifiedTime: Long? = null,
        override val remotePath: String?,
        override val size: Long?,
        val versionId: String,
        val mimeType: String,
        val localPath: String? = null,
        val contentHash: String? = null,
        val contentUrl: String? = null,
        val previewUrl: String? = null,
        val metadata: AssetMetadata? = null,
        val publicLinkId: String? = null,
        override val tags: List<String> = emptyList(),
    ) : Node()
}

@Suppress("MagicNumber")
internal fun CellNode.toFileModel() = Node.File(
    uuid = uuid,
    versionId = versionId,
    name = path.substringAfterLast("/"),
    mimeType = mimeType ?: "",
    remotePath = path,
    contentHash = contentHash,
    contentUrl = contentUrl,
    previewUrl = previews.maxByOrNull { it.dimension }?.url,
    size = size,
    publicLinkId = publicLinkId,
    modifiedTime = modified?.let { it * 1000 },
    tags = tags,
)

@Suppress("MagicNumber")
internal fun CellNode.toFolderModel() = Node.Folder(
    uuid = uuid,
    name = path.substringAfterLast("/"),
    modifiedTime = modified?.let { it * 1000 },
    remotePath = path,
    size = size,
    tags = tags,
)
