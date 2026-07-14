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

import com.wire.kalium.logic.data.message.linkpreview.PreviewBlacklist
import com.wire.kalium.logic.data.message.linkpreview.UrlDetector
import com.wire.kalium.logic.data.message.linkpreview.ExclusionRanges
import com.wire.kalium.logic.data.message.mention.MessageMention

/**
 * Detects the first preview-eligible URL in a message body.
 *
 * Applies the same exclusion and blacklist rules used by link preview generation so callers can
 * reuse one consistent target-selection policy across platforms.
 */
public interface DetectLinkPreviewTargetUseCase {
    public operator fun invoke(
        text: String,
        mentions: List<MessageMention> = emptyList(),
    ): LinkPreviewTarget?
}

/**
 * Represents the detected preview target within the original message text.
 */
public data class LinkPreviewTarget(
    val url: String,
    val position: Int,
)

internal class DetectLinkPreviewTargetUseCaseImpl : DetectLinkPreviewTargetUseCase {
    override operator fun invoke(
        text: String,
        mentions: List<MessageMention>,
    ): LinkPreviewTarget? {
        val excludedRanges = ExclusionRanges.compute(text, mentions)

        return UrlDetector.detect(text, excludedRanges)
            .firstOrNull { !PreviewBlacklist.isBlacklisted(it.url) }
            ?.let { LinkPreviewTarget(it.url, it.start) }
    }
}
