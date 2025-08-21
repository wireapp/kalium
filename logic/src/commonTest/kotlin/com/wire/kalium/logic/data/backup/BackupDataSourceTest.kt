package com.wire.kalium.logic.data.backup

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class BackupDataSourceTest {

    private val userId = UserId("userId", "domain")
    private val testDatabase = TestUserDatabase(UserIDEntity(userId.value, userId.domain))
    private val subject =
        BackupDataSource(userId, testDatabase.builder.userDAO, testDatabase.builder.messageDAO, testDatabase.builder.conversationDAO)

    @Test
    fun givenUsersInDatabase_whenGettingUsers_thenShouldExportThemProperly() = runTest {
        // Given
        val testUsers = listOf(createTestUser("1"), createTestUser("2"))
        subject.insertUsers(testUsers)

        // When
        val result = subject.getUsers()

        // Then
        assertEquals(testUsers, result)
    }

    @Test
    fun givenConversationsInDatabase_whenGettingConversations_thenShouldExportThemProperly() = runTest {
        // Given
        val testConversations = listOf(createTestConversation("1"), createTestConversation("2")).sortedBy { it.id.value }
        subject.insertConversations(testConversations)

        // When
        val result = subject.getConversations().sortedBy { it.id.value }

        // Then
        assertContentEquals(testConversations, result)
    }

    @Test
    fun givenMessagesInDatabase_whenGettingMessages_thenShouldExportThemProperly() = runTest {
        // Given
        val testConversations = listOf(createTestConversation("1"), createTestConversation("2"))
        val testMessages = listOf(
            createTestMessage(testConversations.first().id, "1", userId),
            createTestMessage(testConversations.first().id, "2", userId),
        )
        subject.insertUsers(listOf(createTestUser(userId.value)))
        subject.insertConversations(testConversations)
        subject.insertMessages(testMessages)

        // When
        val result = subject.getMessages().first()

        // Then
        assertEquals(testMessages.size, result.messages.size)
        assertTrue(result.totalPages > 0)
    }

    private fun createTestUser(id: String) = TestUser.OTHER.copy(id = UserId(id, userId.domain))

    private fun createTestConversation(id: String) =
        TestConversation.GROUP().copy(
            id = QualifiedID(id, userId.domain),
            creatorId = userId.value,
            lastModifiedDate = Instant.UNIX_FIRST_DATE,
        )

    private fun createTestMessage(
        conversationId: QualifiedID,
        id: String,
        senderId: UserId,
    ) = TestMessage.TEXT_MESSAGE.copy(
        conversationId = conversationId,
        id = id,
        senderUserId = senderId
    )

}
