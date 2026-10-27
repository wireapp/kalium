/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.logic.feature.giphy

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.giphy.GifPage
import com.wire.kalium.logic.data.giphy.GiphyRepository
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isNotFound

/** Searches Giphy for GIFs rated pg or milder. */
public interface SearchGifsUseCase {
    /**
     * @param offset the [GifPage.nextOffset] of the previous page, or 0 for the first page
     * @param language a two-letter ISO 639-1 code for regional results, or null for Giphy's default
     */
    public suspend operator fun invoke(query: String, offset: Int, language: String?): GetGifsResult
}

internal class SearchGifsUseCaseImpl(private val giphyRepository: GiphyRepository) : SearchGifsUseCase {
    override suspend fun invoke(query: String, offset: Int, language: String?): GetGifsResult =
        giphyRepository.search(query, offset, language).toGetGifsResult()
}

/** A 404 means the backend has no Giphy proxy. */
internal fun Either<NetworkFailure, GifPage>.toGetGifsResult(): GetGifsResult = fold(
    { failure ->
        val exception = (failure as? NetworkFailure.ServerMiscommunication)?.kaliumException
        if (exception is KaliumException.InvalidRequestError && exception.isNotFound()) {
            GetGifsResult.Unavailable
        } else {
            GetGifsResult.Failure(failure)
        }
    },
    { GetGifsResult.Success(it) }
)
