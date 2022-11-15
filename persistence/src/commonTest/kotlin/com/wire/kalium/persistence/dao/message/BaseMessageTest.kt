package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.reaction.ReactionDAO
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlin.test.BeforeTest

open class BaseMessageTest : BaseDatabaseTest() {

    protected lateinit var messageDAO: MessageDAO
    protected lateinit var conversationDAO: ConversationDAO
    protected lateinit var userDAO: UserDAO
    protected lateinit var reactionDAO: ReactionDAO

    @BeforeTest
    fun setUp() {
        deleteDatabase(SELF_USER_ID)
        val db = createDatabase(SELF_USER_ID)

        reactionDAO = db.reactionDAO
        messageDAO = db.messageDAO
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
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
