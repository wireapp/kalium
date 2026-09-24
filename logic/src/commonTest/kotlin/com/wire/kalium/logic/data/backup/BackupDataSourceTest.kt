package com.wire.kalium.logic.data.backup

import com.wire.kalium.common.functional.Either
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
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
import kotlin.time.Instant
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
    fun givenMissingSender_whenImportingMessages_thenPreserveMessagesAndExistingProfiles() = runTest {
        val knownUser = createTestUser("known")
        val missingUser = UserId("missing", userId.domain)
        val conversation = createTestConversation("restore")
        subject.insertUsers(listOf(knownUser))
        subject.insertConversations(listOf(conversation))
        val messages = listOf(
            createTestMessage(conversation.id, "known-message", knownUser.id),
            createTestMessage(conversation.id, "missing-message", missingUser),
        )

        repeat(2) { subject.insertMessages(messages) }

        assertEquals(messages.map { it.id }.toSet(), subject.getMessages().first().data.map { it.id }.toSet())
        assertEquals(knownUser, subject.getUsers().single { it.id == knownUser.id })
        assertTrue(subject.getUsers().any { it.id == missingUser })
    }

    @Test
    fun givenMissingConversationAndSender_whenRestoringTwice_thenPreserveHistoryAndKnownMetadata() = runTest {
        val knownConversation = createTestConversation("known")
        subject.insertConversations(listOf(knownConversation))
        val dao = testDatabase.builder.conversationDAO
        val knownId = QualifiedIDEntity(knownConversation.id.value, userId.domain)
        val knownBefore = assertNotNull(dao.getConversationById(knownId))
        val missingId = QualifiedID("missing", userId.domain)
        val messages = listOf(
            createTestMessage(knownConversation.id, "known-message", userId),
            createTestMessage(missingId, "orphan-message", userId),
            createTestMessage(missingId, "older-message", userId).copy(date = Instant.UNIX_FIRST_DATE),
        )

        repeat(2) { assertTrue(subject.insertMessages(messages) is Either.Right) }

        val restored = subject.getMessages().toList().flatMap { it.data }
        assertEquals(messages.map { it.id }.toSet(), restored.map { it.id }.toSet())
        assertEquals(messages.size, restored.size)
        val placeholder = assertNotNull(dao.getConversationById(QualifiedIDEntity(missingId.value, missingId.domain)))
        assertTrue(placeholder.hasIncompleteMetadata)
        assertTrue(testDatabase.builder.memberDAO.getConversationMembers(placeholder.id).isEmpty())
        assertEquals(messages[1].date, placeholder.lastModifiedDate)
        val knownAfter = assertNotNull(dao.getConversationById(knownId))
        assertEquals(knownBefore.copy(lastModifiedDate = knownAfter.lastModifiedDate), knownAfter)

        // The regular sync write must be able to replace the incomplete metadata later.
        val hydrated = knownBefore.copy(id = placeholder.id)
        dao.insertConversation(hydrated)
        assertEquals(
            hydrated.copy(lastModifiedDate = placeholder.lastModifiedDate, lastReadDate = placeholder.lastReadDate),
            dao.getConversationById(placeholder.id)
        )
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
