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
package com.wire.kalium.cells.data.model

import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.cells.domain.model.NodePreview
import com.wire.kalium.cells.sdk.kmp.model.RestNode

internal data class CellNodeDTO(
    val uuid: String,
    val versionId: String,
    val path: String,
    val modified: Long?,
    val size: Long?,
    val eTag: String? = null,
    val type: String? = null,
    val isRecycled: Boolean = false,
    val isDraft: Boolean = false,
    val contentUrl: String?,
    val contentHash: String?,
    val mimeType: String?,
    val previews: List<PreviewDto> = emptyList(),
    val ownerUserId: String?,
    val conversationId: String?,
    val publicLinkId: String?,
    val tags: List<String> = emptyList(),
)

internal fun CellNodeDTO.toModel() = CellNode(
    uuid = uuid,
    versionId = versionId,
    path = path,
    modified = modified,
    size = size,
    eTag = eTag,
    type = type,
    isRecycled = isRecycled,
    isDraft = isDraft,
    contentUrl = contentUrl,
    contentHash = contentHash,
    mimeType = mimeType,
    previews = previews.map { it.toModel() },
    ownerUserId = ownerUserId,
    conversationId = conversationId,
    publicLinkId = publicLinkId,
    tags = tags
)

internal fun CellNode.toDto() = CellNodeDTO(
    uuid = uuid,
    versionId = versionId,
    path = path,
    modified = modified,
    size = size,
    eTag = eTag,
    type = type,
    isRecycled = isRecycled,
    isDraft = isDraft,
    contentUrl = contentUrl,
    contentHash = contentHash,
    mimeType = mimeType,
    ownerUserId = ownerUserId,
    conversationId = conversationId,
    publicLinkId = publicLinkId,
    tags = tags
)

internal fun RestNode.toDto() = CellNodeDTO(
    uuid = uuid,
    versionId = versionMeta?.versionId ?: "",
    path = path,
    modified = modified?.toLong(),
    size = propertySize?.toLong(),
    type = type?.name ?: "",
    eTag = storageETag,
    isRecycled = isRecycled ?: false,
    isDraft = isDraft ?: false,
    contentUrl = preSignedGET?.url,
    contentHash = contentHash,
    mimeType = contentType,
    previews = previews?.mapNotNull {
        it.preSignedGET?.url?.let { url ->
            PreviewDto(
                url,
                it.dimension ?: 0,
            )
        }
    } ?: emptyList(),
    ownerUserId = userMetadata
        ?.firstOrNull { it.namespace == "usermeta-owner-uuid" }
        ?.jsonValue?.removeSurrounding("\""),
    tags = userMetadata?.firstOrNull { it.namespace == "usermeta-tags" }
        ?.jsonValue
        ?.removeSurrounding("\"")
        ?.split(",")
        ?.map { it.trim() }
        ?: emptyList(),
    conversationId = contextWorkspace?.uuid,
    publicLinkId = shares?.firstOrNull()?.uuid,
)

internal data class PreviewDto(
    val url: String,
    val dimension: Int?,
)

internal fun PreviewDto.toModel() = NodePreview(
    url = url,
    dimension = dimension ?: 0,
)
