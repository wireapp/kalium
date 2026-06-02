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
package com.wire.kalium.logic.feature.message.linkpreview

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.data.message.linkpreview.ExclusionRanges
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewRepository
import com.wire.kalium.logic.data.message.linkpreview.MessageLinkPreview
import com.wire.kalium.logic.data.message.linkpreview.PreviewBlacklist
import com.wire.kalium.logic.data.message.linkpreview.UrlDetector
import com.wire.kalium.logic.data.message.mention.MessageMention

/**
 * Use case for generating link previews from message text.
 *
 * Orchestrates: URL detection → exclusion filtering → blacklist filtering → OG metadata fetching.
 */
public interface GenerateLinkPreviewUseCase {
    public suspend operator fun invoke(
        text: String,
        mentions: List<MessageMention> = emptyList(),
    ): Either<CoreFailure, MessageLinkPreview?>
}

/**
 * Default implementation of GenerateLinkPreviewUseCase.
 */
internal class GenerateLinkPreviewUseCaseImpl(
    private val repository: LinkPreviewRepository
) : GenerateLinkPreviewUseCase {
    override suspend fun invoke(
        text: String,
        mentions: List<MessageMention>
    ): Either<CoreFailure, MessageLinkPreview?> {
        // 1. Detect all URLs
        val excludedRanges = ExclusionRanges.compute(text, mentions)
        val matches = UrlDetector.detect(text, excludedRanges)

        // 2. Filter blacklisted hosts and get first match
        val firstMatch = matches
            .filterNot { PreviewBlacklist.isBlacklisted(it.url) }
            .firstOrNull() ?: return Either.Right(null)

        // 3. Fetch OG metadata
        val originalUrl = firstMatch.url
        val normalizedUrl = normalizeUrl(originalUrl)

        return repository.fetchOpenGraph(normalizedUrl, originalUrl).flatMap { ogData ->
            if (ogData == null) {
                Either.Right(null)
            } else {
                val image = ogData.imageUrls.firstOrNull()?.let { imageUrl ->
                    repository.fetchImage(imageUrl).getOrNull()
                }
                // 4. Map to MessageLinkPreview
                val preview = MessageLinkPreview(
                    url = originalUrl,
                    urlOffset = firstMatch.start,
                    permanentUrl = ogData.url,
                    title = ogData.title,
                    summary = ogData.description,
                    image = image
                )
                Either.Right(preview)
            }
        }
    }

    private fun normalizeUrl(url: String): String {
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else if (url.startsWith("www.")) {
            "https://$url"
        } else {
            "https://$url"
        }
    }
}
