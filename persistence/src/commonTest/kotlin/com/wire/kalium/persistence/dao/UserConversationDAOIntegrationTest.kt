package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UserConversationDAOIntegrationTest : BaseDatabaseTest() {

    private val user1 = User(QualifiedID("1", "wire.com"), "user1", "handle1")
    private val user2 = User(QualifiedID("2", "wire.com"), "user2", "handle2")

    private val conversation1 = Conversation(QualifiedID("1", "wire.com"), "conversation1")

    private val member1 = Member(user1.id)
    private val member2 = Member(user2.id)

    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        val db = createDatabase()
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
    }

    @Test
    fun givenUserExists_whenInsertingMember_thenOriginalUserDetailsAreKept() = runTest {
        userDAO.insertUser(user1)

        conversationDAO.insertConversation(conversation1)
        conversationDAO.insertMember(member1, conversation1.id)

        val result = userDAO.getUserByQualifiedID(user1.id).first()
        assertEquals(user1, result)
    }

}
