package com.wire.kalium.logic.data.backup

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.reaction.MessageReactionWithUsers
import com.wire.kalium.logic.data.message.reaction.MessageReactions
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class BackupDataSourceTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userId = UserId("userId", "domain")
    private val testDatabase = TestUserDatabase(UserIDEntity(userId.value, userId.domain), testDispatcher)
    private val subject =
        BackupDataSource(
            selfUserId = userId,
            userDAO = testDatabase.builder.userDAO,
            messageDAO = testDatabase.builder.messageDAO,
            conversationDAO = testDatabase.builder.conversationDAO,
            reactionDAO = testDatabase.builder.reactionDAO,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

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
        assertEquals(testMessages.size, result.data.size)
        assertTrue(result.totalPages > 0)
    }

    @Test
    fun givenSelfDeletingMessages_whenGettingMessages_thenShouldNotExportThem() = runTest {
        // Given
        val conversation = createTestConversation("1")
        val regularMessage = createTestMessage(conversation.id, "regular", userId)
        val selfDeletingMessageNotStarted = createTestMessage(conversation.id, "self-deleting-not-started", userId).copy(
            expirationData = Message.ExpirationData(expireAfter = 10.seconds)
        )
        val selfDeletingMessageStarted = createTestMessage(conversation.id, "self-deleting-started", userId).copy(
            expirationData = Message.ExpirationData(
                expireAfter = 10.seconds,
                selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.Started(Instant.fromEpochMilliseconds(10_000))
            )
        )
        subject.insertUsers(listOf(createTestUser(userId.value)))
        subject.insertConversations(listOf(conversation))
        subject.insertMessages(listOf(regularMessage, selfDeletingMessageNotStarted, selfDeletingMessageStarted))

        // When
        val result = subject.getMessages().first()

        // Then
        assertEquals(listOf(regularMessage.id), result.data.map { it.id })
    }

    @Test
    fun givenMessagesInMultiplePages_whenGettingMessages_thenAllMessagesAreExported() = runTest {
        // Given
        val numberOfMessages = 1000
        val pageSize = 100
        val testConversations = listOf(createTestConversation("1"), createTestConversation("2"))
        val messageMap = (1..numberOfMessages).associate { index ->
            val message = createTestMessage(testConversations.first().id, index.toString(), userId)
            message.id to (message as Message.Standalone)
        }.toMutableMap()
        subject.insertUsers(listOf(createTestUser(userId.value)))
        subject.insertConversations(testConversations)
        subject.insertMessages(messageMap.values.toList())

        // When
        val allPages = subject.getMessages(pageSize).toList()

        // Then
        assertTrue(allPages.isNotEmpty())
        assertTrue(allPages.first().totalPages > 0)
        allPages.forEach { page ->
            page.data.forEach { message ->
                val originalMessage = messageMap.remove(message.id)
                assertNotNull(originalMessage, "Message was not found in the original map.")
            }
        }
        assertTrue(messageMap.isEmpty(), "Not all messages were found in the export")
    }

    @Test
    fun givenPartiallyRestoredReactionPage_whenRetrying_thenExistingRowsAreIgnoredAndMissingRowsAreInserted() = runTest {
        val conversation = createTestConversation("1")
        val message = createTestMessage(conversation.id, "message", userId)
        subject.insertUsers(listOf(createTestUser(userId.value)))
        subject.insertConversations(listOf(conversation))
        subject.insertMessages(listOf(message))

        subject.insertReactions(
            listOf(
                MessageReactions(
                    messageId = message.id,
                    conversationId = conversation.id,
                    reactions = listOf(MessageReactionWithUsers("existing", listOf(userId))),
                )
            )
        )
        subject.insertReactions(
            listOf(
                MessageReactions(
                    messageId = message.id,
                    conversationId = conversation.id,
                    reactions = listOf(
                        MessageReactionWithUsers("existing", listOf(userId)),
                        MessageReactionWithUsers("new", listOf(userId)),
                    ),
                )
            )
        )

        val result = subject.getReactions().first().data.single().reactions.map { it.emoji }

        assertContentEquals(listOf("existing", "new"), result.sorted())
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
