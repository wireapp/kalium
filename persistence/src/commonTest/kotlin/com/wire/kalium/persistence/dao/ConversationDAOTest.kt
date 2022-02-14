package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.db.Database
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversationDAOTest: BaseDatabaseTest() {

    private val user1 = User(QualifiedID("1", "wire.com"), "user1", "handle1")
    private val user2 = User(QualifiedID("2", "wire.com"), "user2", "handle2")

    private val conversation1 = Conversation(QualifiedID("1", "wire.com"), "conversation1")
    private val conversation2 = Conversation(QualifiedID("2", "wire.com"), "conversation2")

    private val member1 = Member(user1.id)
    private val member2 = Member(user2.id)

    private lateinit var conversationDAO: ConversationDAO

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        val db = createDatabase()
        conversationDAO = db.conversationDAO
    }

    @Test
    fun givenConversation_ThenConversationCanBeInserted() = runTest {
        conversationDAO.insertConversation(conversation1)
        val result = conversationDAO.getConversationByQualifiedID(conversation1.id).first()
        assertEquals(result, conversation1)
    }

    @Test
    fun givenListOfConversations_ThenMultipleConversationsCanBeInsertedAtOnce() = runTest {
        conversationDAO.insertConversations(listOf(conversation1, conversation2))
        val result1 = conversationDAO.getConversationByQualifiedID(conversation1.id).first()
        val result2 = conversationDAO.getConversationByQualifiedID(conversation2.id).first()
        assertEquals(result1, conversation1)
        assertEquals(result2, conversation2)
    }

    @Test
    fun givenExistingConversation_ThenConversationCanBeDeleted() = runTest {
        conversationDAO.insertConversation(conversation1)
        conversationDAO.deleteConversationByQualifiedID(conversation1.id)
        val result = conversationDAO.getConversationByQualifiedID(conversation1.id).first()
        assertNull(result)
    }

    @Test
    fun givenExistingConversation_ThenConversationCanBeUpdated() = runTest {
        conversationDAO.insertConversation(conversation1)
        var updatedConversation1 = Conversation(conversation1.id, "Updated conversation1")
        conversationDAO.updateConversation(updatedConversation1)
        val result = conversationDAO.getConversationByQualifiedID(conversation1.id).first()
        assertEquals(result, updatedConversation1)
    }

    @Test
    fun givenExistingConversation_ThenConversationIsUpdatedOnInsert() = runTest {
        conversationDAO.insertConversation(conversation1)
        var updatedConversation1 = Conversation(conversation1.id, "Updated conversation1")
        conversationDAO.insertConversation(updatedConversation1)
        val result = conversationDAO.getConversationByQualifiedID(conversation1.id).first()
        assertEquals(result, updatedConversation1)
    }

    @Test
    fun givenExistingConversation_ThenMemberCanBeInserted() = runTest {
        conversationDAO.insertConversation(conversation1)
        conversationDAO.insertMember(member1, conversation1.id)

        assertEquals(conversationDAO.getAllMembers(conversation1.id).first(), listOf(member1))
    }

    @Test
    fun givenExistingConversation_ThenMemberCanBeDeleted() = runTest {
        conversationDAO.insertConversation(conversation1)
        conversationDAO.insertMember(member1, conversation1.id)
        conversationDAO.deleteMemberByQualifiedID(conversation1.id, member1.user)

        assertEquals(conversationDAO.getAllMembers(conversation1.id).first(), emptyList())
    }

    @Test
    fun givenExistingConversation_ThenAllMembersCanBeRetrieved() = runTest {
        conversationDAO.insertConversation(conversation1)
        conversationDAO.insertMembers(listOf(member1, member2), conversation1.id)

        assertEquals(conversationDAO.getAllMembers(conversation1.id).first().toSet(), setOf(member1, member2))
    }


}

