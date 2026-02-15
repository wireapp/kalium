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
package com.wire.kalium.logic.feature.backup.mapper

import com.wire.backup.data.BackupMessageContent
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.composite.Button
import com.wire.kalium.logic.framework.TestMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class BackupDataMappersTest {

    @Test
    fun givenMultipartMessageWithNoText_whenMappingToBackup_thenFallbackTextIsUsed() {
        val message = TestMessage.TEXT_MESSAGE.copy(
            content = MessageContent.Multipart(value = null)
        )

        val backupMessage = message.toBackupMessage()

        assertNotNull(backupMessage)
        val backupContent = assertIs<BackupMessageContent.Text>(backupMessage.content)
        assertEquals("[multipart message]", backupContent.text)
    }

    @Test
    fun givenCompositeMessageWithNoText_whenMappingToBackup_thenButtonTextIsUsed() {
        val message = TestMessage.TEXT_MESSAGE.copy(
            content = MessageContent.Composite(
                textContent = null,
                buttonList = listOf(
                    Button(text = "Approve", id = "btn-1", isSelected = false),
                )
            )
        )

        val backupMessage = message.toBackupMessage()

        assertNotNull(backupMessage)
        val backupContent = assertIs<BackupMessageContent.Text>(backupMessage.content)
        assertEquals("Approve", backupContent.text)
    }
}
