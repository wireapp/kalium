/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
@file:OptIn(InternalKaliumApi::class)

package com.wire.kalium.logic.data.message.linkpreview

import com.wire.kalium.util.InternalKaliumApi
import com.wire.kalium.linkpreview.fetch.FetchedPreviewImage
import com.wire.kalium.linkpreview.fetch.LinkPreviewFetcher
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import okio.Buffer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

/** Fetches preview content, interprets OpenGraph metadata, and prepares image assets. */
internal interface LinkPreviewRepository {
    suspend fun fetchOpenGraph(url: String, originalUrl: String): Either<CoreFailure, OpenGraphData?>
    suspend fun fetchImage(imageUrl: String): Either<CoreFailure, LinkPreviewAsset?>
}

@Suppress("TooGenericExceptionCaught")
internal class LinkPreviewRepositoryImpl(
    private val fetcher: LinkPreviewFetcher,
    private val kaliumFileSystem: KaliumFileSystem
) : LinkPreviewRepository {
    override suspend fun fetchOpenGraph(url: String, originalUrl: String): Either<CoreFailure, OpenGraphData?> = try {
        Either.Right(fetcher.fetchHtmlHead(url)?.let { OpenGraphScanner.scan(it, originalUrl) })
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        Either.Right(null)
    }

    override suspend fun fetchImage(imageUrl: String): Either<CoreFailure, LinkPreviewAsset?> = try {
        Either.Right(fetcher.fetchImage(imageUrl)?.toLinkPreviewAsset(imageUrl))
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        Either.Right(null)
    }

    private suspend fun FetchedPreviewImage.toLinkPreviewAsset(imageUrl: String): LinkPreviewAsset? {
        val metadata = readImageMetadata(bytes, mimeType) ?: return null
        val tempPath = kaliumFileSystem.tempFilePath("link-preview-${Uuid.random()}")
        val source = Buffer().write(bytes)
        val sink = kaliumFileSystem.sink(tempPath, mustCreate = true)
        kaliumFileSystem.writeData(sink, source)
        val assetName = sanitizePreviewText(
            input = imageUrl.substringAfterLast('/').substringBefore('?'),
            maxLength = MAX_PREVIEW_ASSET_NAME_LENGTH
        )

        return LinkPreviewAsset(
            mimeType = metadata.mimeType,
            assetDataPath = tempPath,
            assetDataSize = bytes.size.toLong(),
            assetHeight = metadata.height,
            assetWidth = metadata.width,
            assetName = assetName
        )
    }
}
