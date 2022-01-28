package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.db.Database
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConverationDAOTest: BaseDatabaseTest() {

    val user1 = User(QualifiedID("1", "wire.com"), "user1", "handle1")
    val user2 = User(QualifiedID("2", "wire.com"), "user2", "handle2")

    val conversation1 = Conversation(QualifiedID("1", "wire.com"), "conversation1")
    val conversation2 = Conversation(QualifiedID("2", "wire.com"), "conversation2")

    val member1 = Member(user1.id)
    val member2 = Member(user2.id)

    lateinit var db: Database

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        db = createDatabase()
    }

    @Test
    fun givenConversation_ThenConversationCanBeInserted() = runTest {
        db.conversationDAO.insertConversation(conversation1)
        val result = db.conversationDAO.getConversationByQualifiedID(conversation1.id).first()
        assertEquals(result, conversation1)
    }

    @Test
    fun givenListOfConversations_ThenMultipleConversationsCanBeInsertedAtOnce() = runTest {
        db.conversationDAO.insertConversations(listOf(conversation1, conversation2))
        val result1 = db.conversationDAO.getConversationByQualifiedID(conversation1.id).first()
        val result2 = db.conversationDAO.getConversationByQualifiedID(conversation2.id).first()
        assertEquals(result1, conversation1)
        assertEquals(result2, conversation2)
    }

    @Test
    fun givenExistingConversation_ThenConversationCanBeDeleted() = runTest {
        db.conversationDAO.insertConversation(conversation1)
        db.conversationDAO.deleteConversationByQualifiedID(conversation1.id)
        val result = db.conversationDAO.getConversationByQualifiedID(conversation1.id).first()
        assertNull(result)
    }

    @Test
    fun givenExistingConversation_ThenConversationCanBeUpdated() = runTest {
        db.conversationDAO.insertConversation(conversation1)
        var updatedConversation1 = Conversation(conversation1.id, "Updated conversation1")
        db.conversationDAO.updateConversation(updatedConversation1)
        val result = db.conversationDAO.getConversationByQualifiedID(conversation1.id).first()
        assertEquals(result, updatedConversation1)
    }

    @Test
    fun givenExistingConversation_ThenConversationIsUpdatedOnInsert() = runTest {
        db.conversationDAO.insertConversation(conversation1)
        var updatedConversation1 = Conversation(conversation1.id, "Updated conversation1")
        db.conversationDAO.insertConversation(updatedConversation1)
        val result = db.conversationDAO.getConversationByQualifiedID(conversation1.id).first()
        assertEquals(result, updatedConversation1)
    }

    @Test
    fun givenExistingConversation_ThenMemberCanBeInserted() = runTest {
        db.conversationDAO.insertConversation(conversation1)
        db.conversationDAO.insertMember(member1, conversation1.id)

        assertEquals(db.conversationDAO.getAllMembers(conversation1.id).first(), listOf(member1))
    }

    @Test
    fun givenExistingConversation_ThenMemberCanBeDeleted() = runTest {
        db.conversationDAO.insertConversation(conversation1)
        db.conversationDAO.insertMember(member1, conversation1.id)
        db.conversationDAO.deleteMemberByQualifiedID(conversation1.id, member1.user)

        assertEquals(db.conversationDAO.getAllMembers(conversation1.id).first(), emptyList())
    }

    @Test
    fun givenExistingConversation_ThenAllMembersCanBeRetrieved() = runTest {
        db.conversationDAO.insertConversation(conversation1)
        db.conversationDAO.insertMembers(listOf(member1, member2), conversation1.id)

        assertEquals(db.conversationDAO.getAllMembers(conversation1.id).first().toSet(), setOf(member1, member2))
    }


}

