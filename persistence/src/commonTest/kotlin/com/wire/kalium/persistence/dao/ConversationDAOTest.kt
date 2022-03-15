package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversationDAOTest : BaseDatabaseTest() {

    private val user1 = newUserEntity(id = "1")
    private val user2 = newUserEntity(id = "2")

    private val conversationEntity1 = ConversationEntity(QualifiedID("1", "wire.com"), "conversation1")
    private val conversationEntity2 = ConversationEntity(QualifiedID("2", "wire.com"), "conversation2")

    private val member1 = MemberEntity(user1.id)
    private val member2 = MemberEntity(user2.id)

    private lateinit var conversationDAO: ConversationDAO

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        val db = createDatabase()
        conversationDAO = db.conversationDAO
    }

    @Test
    fun givenConversation_ThenConversationCanBeInserted() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        val result = conversationDAO.getConversationByQualifiedID(conversationEntity1.id).first()
        assertEquals(result, conversationEntity1)
    }

    @Test
    fun givenListOfConversations_ThenMultipleConversationsCanBeInsertedAtOnce() = runTest {
        conversationDAO.insertConversations(listOf(conversationEntity1, conversationEntity2))
        val result1 = conversationDAO.getConversationByQualifiedID(conversationEntity1.id).first()
        val result2 = conversationDAO.getConversationByQualifiedID(conversationEntity2.id).first()
        assertEquals(result1, conversationEntity1)
        assertEquals(result2, conversationEntity2)
    }

    @Test
    fun givenExistingConversation_ThenConversationCanBeDeleted() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.deleteConversationByQualifiedID(conversationEntity1.id)
        val result = conversationDAO.getConversationByQualifiedID(conversationEntity1.id).first()
        assertNull(result)
    }

    @Test
    fun givenExistingConversation_ThenConversationCanBeUpdated() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        var updatedConversation1Entity = ConversationEntity(conversationEntity1.id, "Updated conversation1")
        conversationDAO.updateConversation(updatedConversation1Entity)
        val result = conversationDAO.getConversationByQualifiedID(conversationEntity1.id).first()
        assertEquals(result, updatedConversation1Entity)
    }

    @Test
    fun givenExistingConversation_ThenConversationIsUpdatedOnInsert() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        var updatedConversation1Entity = ConversationEntity(conversationEntity1.id, "Updated conversation1")
        conversationDAO.insertConversation(updatedConversation1Entity)
        val result = conversationDAO.getConversationByQualifiedID(conversationEntity1.id).first()
        assertEquals(result, updatedConversation1Entity)
    }

    @Test
    fun givenExistingConversation_ThenMemberCanBeInserted() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.insertMember(member1, conversationEntity1.id)

        assertEquals(conversationDAO.getAllMembers(conversationEntity1.id).first(), listOf(member1))
    }

    @Test
    fun givenExistingConversation_ThenMemberCanBeDeleted() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.insertMember(member1, conversationEntity1.id)
        conversationDAO.deleteMemberByQualifiedID(conversationEntity1.id, member1.user)

        assertEquals(conversationDAO.getAllMembers(conversationEntity1.id).first(), emptyList())
    }

    @Test
    fun givenExistingConversation_ThenAllMembersCanBeRetrieved() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.insertMembers(listOf(member1, member2), conversationEntity1.id)

        assertEquals(conversationDAO.getAllMembers(conversationEntity1.id).first().toSet(), setOf(member1, member2))
    }
}
