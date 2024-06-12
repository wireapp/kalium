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

package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.reaction.ReactionDAO
import com.wire.kalium.persistence.dao.receipt.ReceiptDAO
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlin.test.BeforeTest

open class BaseMessageTest : BaseDatabaseTest() {

    protected lateinit var messageDAO: MessageDAO
    protected lateinit var conversationDAO: ConversationDAO
    protected lateinit var userDAO: UserDAO
    protected lateinit var reactionDAO: ReactionDAO
    protected lateinit var receiptDAO: ReceiptDAO

    @BeforeTest
    fun setUp() {
        deleteDatabase(SELF_USER_ID)
        val db = createDatabase(SELF_USER_ID, encryptedDBSecret, true)

        reactionDAO = db.reactionDAO
        messageDAO = db.messageDAO
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
        receiptDAO = db.receiptDAO
    }

    /**
     * Inserts data needed for general Message tests:
     * ## Users:
     * - [SELF_USER]
     * - [OTHER_USER]
     * - [OTHER_USER_2]
     *
     * ## Conversations:
     * - [TEST_CONVERSATION_1]
     * - [TEST_CONVERSATION_2]
     *
     * ## Messages:
     * - **NO MESSAGES**
     */
    protected open suspend fun insertInitialData() {
        userDAO.upsertUsers(listOf(SELF_USER, OTHER_USER, OTHER_USER_2))
        conversationDAO.insertConversations(listOf(TEST_CONVERSATION_1, TEST_CONVERSATION_2))
    }

    protected companion object {
        val TEST_CONVERSATION_1 = newConversationEntity("testConversation1")
        val TEST_CONVERSATION_2 = newConversationEntity("testConversation2")
        val SELF_USER = newUserEntity("selfUser").copy(name = "selfUser")
        val OTHER_USER = newUserEntity("otherUser").copy(name = "otherUser")
        val OTHER_USER_2 = newUserEntity("otherUser2").copy(name = "otherUser2")
        val SELF_USER_ID = SELF_USER.id
    }
}
