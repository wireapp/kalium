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

package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MessageLinkPreviewTest : BaseDatabaseTest() {

    private lateinit var db: UserDatabaseBuilder

    @BeforeTest
    fun setUp() {
        deleteDatabase(SELF_USER.id)
        db = createDatabase(SELF_USER.id, encryptedDBSecret, true)
    }

    @Test
    fun givenLinkPreviewWithoutPermanentUrl_whenReadingTheMessage_thenPermanentUrlIsTheUrl() = runTest {
        db.userDAO.upsertUsers(listOf(SELF_USER))
        db.conversationDAO.insertConversations(listOf(CONVERSATION))
        db.messageDAO.insertOrIgnoreMessage(
            newRegularMessageEntity(
                id = MESSAGE_ID,
                conversationId = CONVERSATION.id,
                senderUserId = SELF_USER.id,
                content = MessageEntityContent.Text(
                    messageBody = URL,
                    linkPreview = listOf(MessageEntity.LinkPreview(URL, 0, "", "Wire", "Summary"))
                )
            )
        )
        // Kalium stores an empty string for a missing permanent URL; the view falls back only for NULL.
        db.sqlDriver.execute(null, "UPDATE MessageLinkPreview SET permanent_url = NULL", 0)

        val content = db.messageDAO.getMessageById(MESSAGE_ID, CONVERSATION.id)?.content

        assertIs<MessageEntityContent.Text>(content)
        assertEquals(URL, content.linkPreview.single().permanentUrl)
    }

    private companion object {
        const val MESSAGE_ID = "linkPreviewMessage"
        const val URL = "https://wire.com"
        val SELF_USER = newUserEntity("selfUser")
        val CONVERSATION = newConversationEntity("linkPreviewConversation")
    }
}
