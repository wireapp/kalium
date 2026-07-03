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

import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.data.message.linkpreview.ExclusionRanges
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewRepository
import com.wire.kalium.logic.data.message.linkpreview.MessageLinkPreview
import com.wire.kalium.logic.data.message.linkpreview.PreviewBlacklist
import com.wire.kalium.logic.data.message.linkpreview.UrlDetector
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.properties.UserPropertyRepository

/**
 * Use case for generating link previews from message text.
 *
 * Orchestrates: URL detection → exclusion filtering → blacklist filtering → OG metadata fetching.
 */
public interface GenerateLinkPreviewUseCase {
    public suspend operator fun invoke(
        text: String,
        mentions: List<MessageMention> = emptyList(),
    ): MessageLinkPreview?
}

/**
 * Default implementation of GenerateLinkPreviewUseCase.
 */
internal class GenerateLinkPreviewUseCaseImpl(
    private val repository: LinkPreviewRepository,
    private val userPropertyRepository: UserPropertyRepository,
    private val linkPreviewEnabled: Boolean,
) : GenerateLinkPreviewUseCase {
    override suspend fun invoke(
        text: String,
        mentions: List<MessageMention>
    ): MessageLinkPreview? {
        if (!linkPreviewEnabled || !userPropertyRepository.getLinkPreviewsStatus()) return null

        val excludedRanges = ExclusionRanges.compute(text, mentions)
        val matches = UrlDetector.detect(text, excludedRanges)
        val firstMatch = matches
            .filterNot { PreviewBlacklist.isBlacklisted(it.url) }
            .firstOrNull()

        return firstMatch?.let { match ->
            val originalUrl = match.url
            val normalizedUrl = normalizeUrl(originalUrl)
            repository.fetchOpenGraph(normalizedUrl, originalUrl).getOrNull()?.let { ogData ->
                val image = ogData.imageUrls.firstOrNull()?.let { imageUrl ->
                    repository.fetchImage(imageUrl).getOrNull()
                }
                MessageLinkPreview(
                    url = originalUrl,
                    urlOffset = match.start,
                    permanentUrl = ogData.url,
                    title = ogData.title,
                    summary = ogData.description,
                    image = image
                )
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
