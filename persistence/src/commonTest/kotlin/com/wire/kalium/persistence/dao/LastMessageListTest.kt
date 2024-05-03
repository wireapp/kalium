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
package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.MessagePreviewEntityContent
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LastMessageListTest: BaseDatabaseTest() {

    private lateinit var conversationDAO: ConversationDAO
    private lateinit var connectionDAO: ConnectionDAO
    private lateinit var messageDAO: MessageDAO
    private lateinit var userDAO: UserDAO
    private lateinit var teamDAO: TeamDAO
    private lateinit var memberDAO: MemberDAO
    private lateinit var assertDAO: AssetDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, enableWAL = true)
        conversationDAO = db.conversationDAO
        connectionDAO = db.connectionDAO
        messageDAO = db.messageDAO
        userDAO = db.userDAO
        teamDAO = db.teamDAO
        memberDAO = db.memberDAO
        assertDAO = db.assetDAO
    }

    @Test
    fun givenLastMessageIsComposite_thenReturnItAsLastMessage() = runTest {
        val conversion = newConversationEntity("1")
        val user = newUserEntity("1")
        val message = newRegularMessageEntity(
            content = MessageEntityContent.Composite(text = null, buttonList = listOf()),
            conversationId = conversion.id,
            senderUserId = user.id
        )

        conversationDAO.insertConversation(conversion)
        userDAO.upsertUser(user)
        messageDAO.insertOrIgnoreMessage(message)

        messageDAO.observeLastMessages().first().also {
            assertEquals(1, it.size)
            assertEquals(message.id, it.first().id)
            assertEquals(message.conversationId, it.first().conversationId)
            assertIs<MessagePreviewEntityContent.Composite>(it.first().content)
        }
    }
}
