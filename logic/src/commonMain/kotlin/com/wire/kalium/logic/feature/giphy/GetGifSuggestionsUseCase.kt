/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.logic.feature.giphy

import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.logic.data.giphy.GiphyRepository

/** Suggests Giphy search terms that complete [invoke]'s query. */
public interface GetGifSuggestionsUseCase {
    /** @return the suggested terms, or an empty list for a blank query or when Giphy can't be reached */
    public suspend operator fun invoke(query: String): List<String>
}

internal class GetGifSuggestionsUseCaseImpl(private val giphyRepository: GiphyRepository) : GetGifSuggestionsUseCase {
    override suspend fun invoke(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return giphyRepository.searchSuggestions(trimmed).getOrElse(emptyList())
    }
}
