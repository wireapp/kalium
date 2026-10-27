/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.logic.feature.giphy

import com.wire.kalium.logic.data.giphy.GiphyRepository

/** Gets Giphy's trending GIFs rated pg or milder. */
public interface GetTrendingGifsUseCase {
    /** @param offset the [GifPage.nextOffset] of the previous page, or 0 for the first page */
    public suspend operator fun invoke(offset: Int): GetGifsResult
}

internal class GetTrendingGifsUseCaseImpl(private val giphyRepository: GiphyRepository) : GetTrendingGifsUseCase {
    override suspend fun invoke(offset: Int): GetGifsResult = giphyRepository.trending(offset).toGetGifsResult()
}
