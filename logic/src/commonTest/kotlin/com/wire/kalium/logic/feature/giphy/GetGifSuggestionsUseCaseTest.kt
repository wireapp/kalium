/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.logic.feature.giphy

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.giphy.GiphyRepository
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetGifSuggestionsUseCaseTest {

    private val repository = mock<GiphyRepository>()

    @Test
    fun givenQuery_whenGettingSuggestions_thenReturnsTheTrimmedQuerysSuggestions() = runTest {
        everySuspend { repository.searchSuggestions("happy fri") } returns Either.Right(listOf("happy friday"))

        assertEquals(listOf("happy friday"), GetGifSuggestionsUseCaseImpl(repository)("  happy fri "))
    }

    @Test
    fun givenBlankQuery_whenGettingSuggestions_thenReturnsNothingWithoutARequest() = runTest {
        assertEquals(emptyList(), GetGifSuggestionsUseCaseImpl(repository)("   "))

        verifySuspend(VerifyMode.not) { repository.searchSuggestions(any()) }
    }

    @Test
    fun givenFailure_whenGettingSuggestions_thenReturnsNothing() = runTest {
        everySuspend { repository.searchSuggestions(any()) } returns Either.Left(NetworkFailure.NoNetworkConnection(null))

        assertEquals(emptyList(), GetGifSuggestionsUseCaseImpl(repository)("cat"))
    }
}
