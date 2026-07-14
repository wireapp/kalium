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

import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.user.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetectLinkPreviewTargetUseCaseTest {

    private val useCase = DetectLinkPreviewTargetUseCaseImpl()

    @Test
    fun givenTextWithLink_whenDetecting_thenReturnsFirstLinkAndPosition() {
        val result = useCase("hello https://wire.com world")

        assertEquals(
            LinkPreviewTarget(
                url = "https://wire.com",
                position = 6
            ),
            result
        )
    }

    @Test
    fun givenMarkdownLink_whenDetecting_thenReturnsNull() {
        val result = useCase("[Wire](https://wire.com)")

        assertNull(result)
    }

    @Test
    fun givenMentionedUrl_whenDetecting_thenSkipsExcludedMentionRange() {
        val text = "@wire.com hello"

        val result = useCase(
            text = text,
            mentions = listOf(
                MessageMention(
                    start = 0,
                    length = 9,
                    userId = UserId("user", "wire.com"),
                    isSelfMention = false
                )
            )
        )

        assertNull(result)
    }

    @Test
    fun givenBlacklistedLinkBeforeValidLink_whenDetecting_thenReturnsFirstAllowedLink() {
        val result = useCase("https://giphy.com/test https://wire.com")

        assertEquals(
            LinkPreviewTarget(
                url = "https://wire.com",
                position = 23
            ),
            result
        )
    }
}
