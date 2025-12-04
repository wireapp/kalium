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

import com.wire.kalium.cells.domain.model.NodeVersion
import com.wire.kalium.cells.domain.model.toDto
import com.wire.kalium.cells.sdk.kmp.model.RestVersion

internal fun RestVersion.toDto() = NodeVersionDTO(
    id = versionId,
    hash = contentHash,
    description = description,
    isDraft = draft,
    etag = etag,
    editorUrls = editorURLs?.mapValues { it.value.toDto() },
    filePreviews = filePreviews?.map { it.toDto() },
    isHead = isHead,
    modifiedTime = mtime,
    ownerName = ownerName,
    ownerUuid = ownerUuid,
    getUrl = preSignedGET?.toDto(),
    size = propertySize
)

internal fun NodeVersionDTO.toModel() = NodeVersion(
    id = id,
    hash = hash,
    description = description,
    isDraft = isDraft,
    etag = etag,
    editorUrls = editorUrls,
    filePreviews = filePreviews,
    isHead = isHead,
    modifiedTime = modifiedTime,
    ownerName = ownerName,
    ownerUuid = ownerUuid,
    getUrl = getUrl,
    size = size
)
