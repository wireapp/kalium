/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.logic.data.giphy

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.network.api.authenticated.giphy.GiphyGifDTO
import com.wire.kalium.network.api.authenticated.giphy.GiphyGifsResponseDTO
import com.wire.kalium.network.api.authenticated.giphy.GiphyImageDTO
import com.wire.kalium.network.api.base.authenticated.GiphyApi
import okio.IOException
import okio.Path
import okio.use
import kotlin.uuid.Uuid

internal interface GiphyRepository {
    suspend fun search(query: String, offset: Int, language: String?): Either<NetworkFailure, GifPage>
    suspend fun trending(offset: Int): Either<NetworkFailure, GifPage>
    suspend fun searchSuggestions(query: String): Either<NetworkFailure, List<String>>
    suspend fun download(gif: Gif): Either<CoreFailure, DownloadedGif>
}

internal class GiphyDataSource(
    private val giphyApi: GiphyApi,
    private val kaliumFileSystem: KaliumFileSystem,
) : GiphyRepository {

    override suspend fun search(query: String, offset: Int, language: String?): Either<NetworkFailure, GifPage> =
        wrapApiRequest { giphyApi.search(query, PAGE_SIZE, offset, RATING, language) }.map { it.toGifPage() }

    override suspend fun trending(offset: Int): Either<NetworkFailure, GifPage> =
        wrapApiRequest { giphyApi.trending(PAGE_SIZE, offset, RATING) }.map { it.toGifPage() }

    override suspend fun searchSuggestions(query: String): Either<NetworkFailure, List<String>> =
        wrapApiRequest { giphyApi.searchSuggestions(query) }.map { response -> response.data.map { it.name } }

    override suspend fun download(gif: Gif): Either<CoreFailure, DownloadedGif> {
        val path = kaliumFileSystem.tempFilePath("giphy-${Uuid.random()}.gif")
        var downloaded = false
        try {
            return writeGif(gif.original.url, path)
                .map { size -> DownloadedGif(gif, path, size) }
                .onSuccess { downloaded = true }
        } finally {
            // Also runs when the preview is abandoned mid-download, so no partial file is left behind.
            if (!downloaded) runCatching { kaliumFileSystem.delete(path) }
        }
    }

    private suspend fun writeGif(url: String, path: Path): Either<CoreFailure, Long> = try {
        wrapApiRequest { kaliumFileSystem.sink(path).use { sink -> giphyApi.downloadGif(url, MAX_GIF_SIZE_BYTES, sink) } }
    } catch (e: IOException) {
        Either.Left(StorageFailure.Generic(e))
    }

    private fun GiphyGifsResponseDTO.toGifPage(): GifPage {
        val nextOffset = pagination?.let { page ->
            (page.offset + page.count).takeIf { next ->
                page.count > 0 && next <= MAX_OFFSET && next < (page.totalCount ?: Int.MAX_VALUE)
            }
        }
        return GifPage(gifs = data.mapNotNull { it.toGif() }, nextOffset = nextOffset)
    }

    /** Skips GIFs whose sent rendition is missing, of unknown size or too large, as web and the old iOS app do. */
    private fun GiphyGifDTO.toGif(): Gif? {
        val original = images.downsized?.toRendition()
            ?.takeIf { (it.sizeInBytes ?: Long.MAX_VALUE) <= MAX_GIF_SIZE_BYTES }
            ?: return null
        val preview = (images.fixedWidthDownsampled ?: images.fixedWidth)?.toRendition() ?: original
        return Gif(id = id, title = cleanTitle(title.orEmpty()), preview = preview, original = original)
    }

    private fun GiphyImageDTO.toRendition(): GifRendition? {
        val url = url
        val width = width?.toIntOrNull()
        val height = height?.toIntOrNull()
        return if (url.isNullOrBlank() || width == null || height == null) {
            null
        } else {
            GifRendition(url = url, width = width, height = height, sizeInBytes = size?.toLongOrNull())
        }
    }

    internal companion object {
        const val PAGE_SIZE = 24
        const val RATING = "pg-13"
        const val MAX_GIF_SIZE_BYTES = 3L * 1024 * 1024

        /** Giphy rejects larger offsets. */
        const val MAX_OFFSET = 4999

        private val TITLE_SUFFIX = Regex("""\s+GIF(\s+by\s+.*)?$""", RegexOption.IGNORE_CASE)

        /** "Happy Friday Dance GIF by Studio" becomes "Happy Friday Dance". */
        fun cleanTitle(title: String): String = title.replace(TITLE_SUFFIX, "").trim()
    }
}
