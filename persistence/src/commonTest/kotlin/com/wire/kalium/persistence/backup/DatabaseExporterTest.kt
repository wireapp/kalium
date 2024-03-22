/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.kalium.persistence.backup

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.IgnoreIOS
import com.wire.kalium.persistence.utils.IgnoreJvm
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserDetailsEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class)
@IgnoreJvm
@IgnoreIOS
class DatabaseExporterTest : BaseDatabaseTest() {
    private lateinit var localDB: UserDatabaseBuilder

    private val selfUserId = UserIDEntity("selfValue", "selfDomain")
    private val backupUserId = UserIDEntity("backup-selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        localDB = createDatabase(selfUserId, passphrase = null, enableWAL = false)

        runTest {
            with(localDB.userDAO) {
                upsertUser(SELF_USER.toSimpleEntity())
                upsertUser(OTHER_USER.toSimpleEntity())
                upsertUser(OTHER_USER_2.toSimpleEntity())
            }

            with(localDB.conversationDAO) {
                insertConversation(TEST_CONVERSATION_1)
                insertConversation(TEST_CONVERSATION_2)
            }
        }
    }

    @Test
    fun givenSelfDeletingMessages_whenBackup_thenTheyAreNotIncludedInTheGeneratedBackup() = runTest {
        val selfDeleteMessage =
            OTHER_MESSAGE.copy(id = "selfDelete", expireAfterMs = 10000, sender = OTHER_USER, senderUserId = OTHER_USER.id)
        val normalMessage = OTHER_MESSAGE.copy(id = "normal", expireAfterMs = null, sender = OTHER_USER, senderUserId = OTHER_USER.id)

        localDB.messageDAO.insertOrIgnoreMessages(listOf(selfDeleteMessage, normalMessage))

        localDB.messageDAO.getMessageById(selfDeleteMessage.id, selfDeleteMessage.conversationId)?.also {
            assertEquals(selfDeleteMessage, it)
        } ?: fail("Message should not be null")

        localDB.databaseExporter.exportToPlainDB(null) ?: fail("Backup should not be null")

        createDatabase(backupUserId, passphrase = null, enableWAL = false).also { backupDB ->
            backupDB.messageDAO.getMessageById(selfDeleteMessage.id, selfDeleteMessage.conversationId)?.also {
                fail("Message should be null")
            }
            backupDB.messageDAO.getMessageById(normalMessage.id, normalMessage.conversationId)?.also {
                assertEquals(normalMessage, it)
            } ?: fail("Message should not be null")
        }
    }

    private companion object {
        val TEST_CONVERSATION_1 = newConversationEntity("testConversation1")
        val TEST_CONVERSATION_2 = newConversationEntity("testConversation2")
        val SELF_USER = newUserDetailsEntity("selfUser").copy(name = "selfUser")
        val OTHER_USER = newUserDetailsEntity("otherUser").copy(name = "otherUser")
        val OTHER_USER_2 = newUserDetailsEntity("otherUser2").copy(name = "otherUser2")
        val SELF_USER_ID = SELF_USER.id

        val ORIGINAL_MESSAGE_SENDER = OTHER_USER
        val SELF_MENTION = MessageEntity.Mention(
            start = 0, length = 9, userId = SELF_USER_ID
        )
        val OTHER_MENTION = MessageEntity.Mention(
            start = 10, length = 11, userId = OTHER_USER_2.id
        )

        const val OTHER_MESSAGE_CONTENT = "Something to think about"
        val OTHER_MESSAGE = newRegularMessageEntity(
            id = "OTHER_MESSAGE",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = ORIGINAL_MESSAGE_SENDER.id,
            senderName = ORIGINAL_MESSAGE_SENDER.name!!,
            content = MessageEntityContent.Text(OTHER_MESSAGE_CONTENT)
        )

        val OTHER_QUOTING_OTHERS = newRegularMessageEntity(
            id = "OTHER_QUOTING_OTHERS",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = ORIGINAL_MESSAGE_SENDER.id,
            content = MessageEntityContent.Text(
                "I'm quoting others", quotedMessageId = OTHER_MESSAGE.id
            )
        )

        val OTHER_MENTIONING_OTHERS = newRegularMessageEntity(
            id = "OTHER_MENTIONING_OTHERS",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = ORIGINAL_MESSAGE_SENDER.id,
            content = MessageEntityContent.Text(
                messageBody = "@$@${OTHER_USER_2.name}", mentions = listOf(OTHER_MENTION)
            )
        )

        val SELF_MESSAGE = newRegularMessageEntity(
            id = "SELF_MESSAGE",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER_ID,
            content = MessageEntityContent.Text(OTHER_MESSAGE_CONTENT)
        )

        val OTHER_QUOTING_SELF = newRegularMessageEntity(
            id = "OTHER_QUOTING_SELF",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = ORIGINAL_MESSAGE_SENDER.id,
            content = MessageEntityContent.Text(
                "I'm quoting selfUser", quotedMessageId = SELF_MESSAGE.id
            )
        )

        val OTHER_MENTIONING_SELF = newRegularMessageEntity(
            id = "OTHER_MENTIONING_SELF",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = ORIGINAL_MESSAGE_SENDER.id,
            content = MessageEntityContent.Text(
                messageBody = "@${SELF_USER.name} @${OTHER_USER_2.name}", mentions = listOf(SELF_MENTION)
            )
        )
    }
}
