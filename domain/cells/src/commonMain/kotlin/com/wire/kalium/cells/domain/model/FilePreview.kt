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

import com.wire.kalium.cells.sdk.kmp.model.RestFilePreview

public data class FilePreview(
    val bucket: String?,
    val contentType: String?,
    val dimension: Int?,
    val error: Boolean?,
    val key: String?,
    val getUrl: PreSignedUrl?,
    val processing: Boolean?
)

internal fun RestFilePreview.toDto() = FilePreview(
    bucket = bucket,
    contentType = contentType,
    dimension = dimension,
    error = error,
    key = key,
    getUrl = preSignedGET?.toDto(),
    processing = processing
)
