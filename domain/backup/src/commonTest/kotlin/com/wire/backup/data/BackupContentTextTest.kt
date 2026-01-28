/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.backup.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackupContentTextTest {

    @Test
    fun givenMentionTooLong_whenCreatingText_thenShouldThrowIAE() {
        val text = "Hello"
        val mention = BackupMessageContent.Text.Mention(
            userId = BackupQualifiedId(id = "user", domain = "example.com"),
            start = 3,
            length = 5, // 3 + 5 = 8 > text.length (5)
        )

        assertFailsWith<IllegalArgumentException> {
            BackupMessageContent.Text(text = text, mentions = listOf(mention))
        }
    }

    @Test
    fun givenMultipleMentionsWithOnlyOneOutOfBounds_whenCreatingText_thenShouldThrowIAE() {
        val text = "Hello @alice"
        val validMention = BackupMessageContent.Text.Mention(
            userId = BackupQualifiedId(id = "alice-id", domain = "example.com"),
            start = 6,
            length = 6, // Valid: 6 + 6 = 12 == text.length
        )
        val invalidMention = BackupMessageContent.Text.Mention(
            userId = BackupQualifiedId(id = "bob-id", domain = "example.com"),
            start = 10,
            length = 5, // Invalid: 10 + 5 = 15 > text.length (12)
        )

        assertFailsWith<IllegalArgumentException> {
            BackupMessageContent.Text(text = text, mentions = listOf(validMention, invalidMention))
        }
    }


    @Test
    fun givenNegativeStart_whenCreatingMention_thenShouldThrowIAE() {
        assertFailsWith<IllegalArgumentException> {
            BackupMessageContent.Text.Mention(
                userId = BackupQualifiedId(id = "user", domain = "example.com"),
                start = -1,
                length = 1,
            )
        }
    }

    @Test
    fun givenZeroLength_whenCreatingMention_thenShouldThrowIAE() {
        assertFailsWith<IllegalArgumentException> {
            BackupMessageContent.Text.Mention(
                userId = BackupQualifiedId(id = "user", domain = "example.com"),
                start = 0,
                length = 0,
            )
        }
    }

    @Test
    fun givenValidMentionAtTextEnd_whenCreatingText_thenShouldNotThrow() {
        val text = "Hello"
        // Mention ends exactly at the end of the text: start + length == text.length
        val mention = BackupMessageContent.Text.Mention(
            userId = BackupQualifiedId(id = "user", domain = "example.com"),
            start = 2,
            length = 3,
        )

        val content = BackupMessageContent.Text(text = text, mentions = listOf(mention))
        assertEquals(1, content.mentions.size)
        assertEquals(text, content.text)
    }

    @Test
    fun givenNoMentions_whenCreatingText_thenShouldNotThrow() {
        val text = "Just text, no mentions"
        val content = BackupMessageContent.Text(text = text, mentions = emptyList())
        assertEquals(0, content.mentions.size)
        assertEquals(text, content.text)
    }

    @Test
    fun givenMultipleValidMentions_whenCreatingText_thenShouldNotThrow() {
        val text = "Say hi to @alice and @bob!"
        val alice = BackupMessageContent.Text.Mention(
            userId = BackupQualifiedId(id = "alice-id", domain = "example.com"),
            start = 11, // position of '@' in "@alice"
            length = 6,
        )
        val bob = BackupMessageContent.Text.Mention(
            userId = BackupQualifiedId(id = "bob-id", domain = "example.com"),
            start = 22, // position of '@' in "@bob"
            length = 4,
        )

        val content = BackupMessageContent.Text(text = text, mentions = listOf(alice, bob))
        assertEquals(2, content.mentions.size)
    }
}
