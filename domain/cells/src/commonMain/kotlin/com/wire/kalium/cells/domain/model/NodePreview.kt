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

/**
 * One of the renditions the backend generates for a node, served by its own pre-signed URL.
 *
 * A node can have several: image previews of different sizes, and for documents it can convert
 * (documents, presentations, spreadsheets) a PDF rendition of the whole file.
 *
 * @param url pre-signed URL of this rendition.
 * @param dimension max thumbnail dimension, for image renditions.
 * @param contentType MIME type of the rendition, which is what tells them apart.
 */
public data class NodePreview(
    val url: String,
    val dimension: Int,
    val contentType: String? = null,
)

/** The largest image rendition, which is what a thumbnail is drawn from. */
public fun List<NodePreview>?.imagePreviewUrl(): String? = this
    ?.filter { it.contentType?.startsWith(IMAGE_CONTENT_TYPE_PREFIX) == true }
    ?.maxByOrNull { it.dimension }
    ?.url

/** The PDF rendition, which is what lets a document be displayed without an editor. */
public fun List<NodePreview>?.pdfPreviewUrl(): String? = this
    ?.firstOrNull { it.contentType?.startsWith(PDF_CONTENT_TYPE) == true }
    ?.url

private const val IMAGE_CONTENT_TYPE_PREFIX = "image/"
private const val PDF_CONTENT_TYPE = "application/pdf"

