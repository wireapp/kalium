/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.logic.feature.giphy

import com.wire.kalium.common.error.serverMiscommunication

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.giphy.GifPage
import com.wire.kalium.logic.data.giphy.GiphyRepository
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class SearchGifsUseCaseTest {

    private val repository = mock<GiphyRepository>()

    @Test
    fun givenResults_whenSearching_thenReturnsThePage() = runTest {
        val page = GifPage(gifs = emptyList(), nextOffset = 24)
        everySuspend { repository.search(any(), any(), any()) } returns Either.Right(page)

        assertEquals(GetGifsResult.Success(page), SearchGifsUseCaseImpl(repository)("cat", 0, null))
    }

    @Test
    fun givenBackendWithoutTheProxy_whenSearching_thenGifsAreUnavailable() = runTest {
        everySuspend { repository.search(any(), any(), any()) } returns Either.Left(serverError(404))

        assertEquals(GetGifsResult.Unavailable, SearchGifsUseCaseImpl(repository)("cat", 0, null))
    }

    @Test
    fun givenServerError_whenFetchingTrending_thenFails() = runTest {
        everySuspend { repository.trending(any()) } returns Either.Left(serverError(502))

        assertIs<GetGifsResult.Failure>(GetTrendingGifsUseCaseImpl(repository)(0))
    }

    @Test
    fun givenNoNetwork_whenSearching_thenFails() = runTest {
        everySuspend { repository.search(any(), any(), any()) } returns Either.Left(NetworkFailure.NoNetworkConnection(null))

        assertIs<GetGifsResult.Failure>(SearchGifsUseCaseImpl(repository)("cat", 0, null))
    }

    private fun serverError(code: Int) = serverMiscommunication(
        if (code < 500) {
            KaliumException.InvalidRequestError(GenericAPIErrorResponse(code, "error", "error"))
        } else {
            KaliumException.ServerError(GenericAPIErrorResponse(code, "error", "error"))
        }
    )
}
