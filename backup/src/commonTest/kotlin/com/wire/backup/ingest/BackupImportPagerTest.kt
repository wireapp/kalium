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
package com.wire.backup.ingest

import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupDateTime
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupMessageContent
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.data.BackupUser
import com.wire.backup.filesystem.BackupPage
import com.wire.kalium.protobuf.backup.BackupData
import com.wire.kalium.protobuf.backup.BackupInfo
import com.wire.kalium.protobuf.backup.ExportedQualifiedId
import com.wire.kalium.protobuf.encodeToByteArray
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupImportPagerTest {

    @Test
    fun givenMultiplePages_whenConsuming_thenShouldSplitBetweenDifferentContentCorrectly() {
        val expectedConversations = listOf(
            fakeConversation(0),
            fakeConversation(1),
            fakeConversation(2),
            fakeConversation(3),
        )
        val user0 = BackupUser(fakeId(0), "USER 0", "user")
        val expectedMessages = listOf(
            fakeMessage(0),
            fakeMessage(1)
        )
        val expectedUsers = listOf(
            user0,
            user0.copy(id = fakeId(1))
        )
        val pages = listOf(
            fakeBackupPage(BackupPage.CONVERSATIONS_PREFIX + "0", expectedConversations[0]),
            fakeBackupPage(BackupPage.MESSAGES_PREFIX + "1", message = expectedMessages[1]),
            fakeBackupPage(BackupPage.CONVERSATIONS_PREFIX + "11", expectedConversations[3]),
            fakeBackupPage(BackupPage.MESSAGES_PREFIX + "0", message = expectedMessages[0]),
            fakeBackupPage(BackupPage.USERS_PREFIX + "0", user = expectedUsers[0]),
            fakeBackupPage(BackupPage.CONVERSATIONS_PREFIX + "1", expectedConversations[1]),
            fakeBackupPage(BackupPage.USERS_PREFIX + "1", user = expectedUsers[1]),
            fakeBackupPage(BackupPage.CONVERSATIONS_PREFIX + "2", expectedConversations[2]),
        )
        val pager = BackupImportPager(pages)

        assertTrue { pager.usersPager.hasMorePages() }
        assertEquals(expectedUsers.size, pager.usersPager.totalPages)
        assertEquals(expectedUsers[0], pager.usersPager.nextPage().first())
        assertEquals(expectedUsers[1], pager.usersPager.nextPage().first())
        assertFalse { pager.usersPager.hasMorePages() }

        assertTrue { pager.conversationsPager.hasMorePages() }
        assertEquals(expectedConversations.size, pager.conversationsPager.totalPages)
        assertEquals(expectedConversations[0], pager.conversationsPager.nextPage().first())
        assertEquals(expectedConversations[1], pager.conversationsPager.nextPage().first())
        assertEquals(expectedConversations[2], pager.conversationsPager.nextPage().first())
        assertEquals(expectedConversations[3], pager.conversationsPager.nextPage().first())
        assertFalse { pager.conversationsPager.hasMorePages() }

        assertTrue { pager.messagesPager.hasMorePages() }
        assertEquals(expectedMessages.size, pager.messagesPager.totalPages)
        assertEquals(expectedMessages[0], pager.messagesPager.nextPage().first())
        assertEquals(expectedMessages[1], pager.messagesPager.nextPage().first())
        assertFalse { pager.messagesPager.hasMorePages() }
    }

    @Test
    fun givenNoPages_whenConsuming_thenShouldCorrectlySayNoMorePages() {
        val pager = BackupImportPager(listOf())

        assertEquals(0, pager.usersPager.totalPages)
        assertFalse { pager.usersPager.hasMorePages() }
        assertFalse { pager.messagesPager.hasMorePages() }
        assertFalse { pager.conversationsPager.hasMorePages() }
    }

    private fun fakeConversation(id: Int) = BackupConversation(BackupQualifiedId("conv$id", "domain"), "$id")

    private fun fakeMessage(id: Int) = BackupMessage(
        "message$id",
        BackupQualifiedId("conv0", "domain"),
        fakeId(id),
        "clientId",
        BackupDateTime(42L),
        BackupMessageContent.Text("hey$id")
    )

    private fun fakeId(id: Int) = BackupQualifiedId("id$id", "domain")

    private fun fakeBackupPage(
        backupPageName: String,
        conversation: BackupConversation? = null,
        message: BackupMessage? = null,
        user: BackupUser? = null,
    ): BackupPage {
        val mapper = MPBackupMapper()
        val data = BackupData(
            BackupInfo(
                platform = "Platform",
                version = "Version",
                userId = ExportedQualifiedId("id", "domain"),
                creationTime = 42L,
                clientId = "clientId"
            ),
            conversations = conversation?.let { listOf(mapper.mapConversationToProtobuf(it)) } ?: listOf(),
            users = user?.let { listOf(mapper.mapUserToProtobuf(it)) } ?: listOf(),
            messages = message?.let { listOf(mapper.mapMessageToProtobuf(it)) } ?: listOf()
        )
        val buffer = Buffer()
        buffer.write(data.encodeToByteArray())
        return BackupPage(backupPageName + BackupPage.PAGE_SUFFIX, buffer.copy())
    }
}
