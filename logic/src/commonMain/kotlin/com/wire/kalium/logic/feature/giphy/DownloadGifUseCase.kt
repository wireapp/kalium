/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.logic.feature.giphy

import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.giphy.Gif
import com.wire.kalium.logic.data.giphy.GiphyRepository

/**
 * Downloads a GIF's [Gif.original] rendition from Giphy's CDN to a temporary file.
 * Fails if the response isn't a GIF file (`GIF87a` or `GIF89a` header), so nothing else is sent as `image/gif`.
 * Show that file as the preview and pass it to [SendGifMessageUseCase], so the GIF is downloaded only once.
 */
public interface DownloadGifUseCase {
    public suspend operator fun invoke(gif: Gif): DownloadGifResult
}

internal class DownloadGifUseCaseImpl(private val giphyRepository: GiphyRepository) : DownloadGifUseCase {
    override suspend fun invoke(gif: Gif): DownloadGifResult = giphyRepository.download(gif).fold(
        { DownloadGifResult.Failure(it) },
        { DownloadGifResult.Success(it) }
    )
}
