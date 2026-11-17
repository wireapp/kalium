/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.logic.data.giphy

import okio.Path

/**
 * A GIF from Giphy.
 * @property title Giphy's title without its "GIF by …" suffix; may be empty.
 * @property preview A small rendition for result grids.
 * @property original The rendition that is sent; never larger than 3 MiB.
 * @property creator Who made the GIF, null when Giphy names no one.
 */
public data class Gif(
    val id: String,
    val title: String,
    val preview: GifRendition,
    val original: GifRendition,
    val creator: GifCreator? = null,
)

/** The Giphy channel or user that made a GIF, shown as its attribution. */
public data class GifCreator(
    val username: String,
    val avatarUrl: String?,
    val isVerified: Boolean,
)

/** One file Giphy offers for a GIF. [sizeInBytes] is null when Giphy does not report it. */
public data class GifRendition(
    val url: String,
    val width: Int,
    val height: Int,
    val sizeInBytes: Long?,
)

/** @property nextOffset The offset of the next page, or null when there are no more results. */
public data class GifPage(
    val gifs: List<Gif>,
    val nextOffset: Int?,
)

/** A GIF's [Gif.original] rendition, downloaded to a temporary file ready to preview and send. */
public data class DownloadedGif(
    val gif: Gif,
    val path: Path,
    val sizeInBytes: Long,
)
