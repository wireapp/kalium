package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UserConversationDAOIntegrationTest : BaseDatabaseTest() {

    private val user1 = newUserEntity(id = "1")
    private val user2 = newUserEntity(id = "2")

    private val conversationEntity1 =
        ConversationEntity(
            QualifiedIDEntity("1", "wire.com"),
            "conversation1",
            ConversationEntity.Type.ONE_ON_ONE,
            "teamID",
            ConversationEntity.ProtocolInfo.Proteus,
            lastNotificationDate = null,
            lastModifiedDate = null
        )

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

        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.insertMember(member1, conversationEntity1.id)

        val result = userDAO.getUserByQualifiedID(user1.id).first()
        assertEquals(user1, result)
    }
}
