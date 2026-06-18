package com.wire.kalium.logic.data.backup

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.composite.Button
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
            messageThreadDAO = testDatabase.builder.messageThreadDAO,
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
    fun givenMultipartAndCompositeMessagesInDatabase_whenGettingMessages_thenTheyAreExported() = runTest {
        // Given
        val testConversations = listOf(createTestConversation("1"), createTestConversation("2"))
        val testMessages = listOf(
            createMultipartTestMessage(testConversations.first().id, "multipart-message", userId),
            createCompositeTestMessage(testConversations.first().id, "composite-message", userId),
        )
        subject.insertUsers(listOf(createTestUser(userId.value)))
        subject.insertConversations(testConversations)
        subject.insertMessages(testMessages)

        // When
        val result = subject.getMessages().first()

        // Then
        assertEquals(testMessages.size, result.data.size)
        assertEquals(testMessages.map { it.id }.toSet(), result.data.map { it.id }.toSet())
        assertTrue(result.data.any { it is Message.Regular && it.content is MessageContent.Multipart })
        assertTrue(result.data.any { it is Message.Regular && it.content is MessageContent.Composite })
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
    fun givenMessageThreadData_whenGettingThreadIdForMessage_thenThreadIdIsReturned() = runTest {
        val conversationId = createTestConversation("thread-conversation").id
        val rootMessage = createTestMessage(conversationId, "root-message-id", userId)
        val threadId = rootMessage.id

        subject.insertUsers(listOf(createTestUser(userId.value)))
        subject.insertConversations(listOf(createTestConversation("thread-conversation")))
        subject.insertMessages(listOf(rootMessage))

        val threadData = listOf(
            BackupThreadData(
                conversationId = conversationId,
                messageId = rootMessage.id,
                threadId = threadId,
                isRoot = true,
                creationDate = rootMessage.date,
            )
        )
        subject.insertThreadData(threadData)

        val storedThreadId = subject.getThreadIdForMessage(conversationId, rootMessage.id)

        assertEquals(threadId, storedThreadId)
    }

    @Test
    fun givenRestoredThreadData_whenReadingMainList_thenRootAndMainMessagesAreShownAndRepliesAreHidden() = runTest {
        val conversation = createTestConversation("restore-thread-conversation")
        val root = createTestMessage(conversation.id, "restore-root-id", userId)
        val reply = createTestMessage(conversation.id, "restore-reply-id", userId)
        val mainMessage = createTestMessage(conversation.id, "restore-main-id", userId)

        subject.insertUsers(listOf(createTestUser(userId.value)))
        subject.insertConversations(listOf(conversation))
        subject.insertMessages(listOf(root, reply, mainMessage))
        subject.insertThreadRoots(
            listOf(
                BackupThreadRootData(
                    conversationId = conversation.id,
                    rootMessageId = root.id,
                    threadId = root.id,
                    createdAt = root.date,
                )
            )
        )
        subject.insertThreadItems(
            listOf(
                BackupThreadItemData(conversation.id, root.id, root.id, isRoot = true, root.date),
                BackupThreadItemData(conversation.id, reply.id, root.id, isRoot = false, reply.date),
            )
        )
        subject.refreshThreadMetadata(setOf(BackupThreadReference(conversation.id, root.id)))

        val mainListIds = testDatabase.builder.messageDAO.getMessagesByConversationAndVisibility(
            conversationId = conversation.id.toDao(),
            limit = 50,
            offset = 0,
            visibility = listOf(MessageEntity.Visibility.VISIBLE)
        ).first().map { it.id }
        val threadSummary = testDatabase.builder.messageThreadDAO
            .observeThreadSummariesForRoots(conversation.id.toDao(), listOf(root.id))
            .first()
            .single()

        assertContains(mainListIds, root.id)
        assertContains(mainListIds, mainMessage.id)
        assertFalse(mainListIds.contains(reply.id))
        assertEquals(1L, threadSummary.visibleReplyCount)
    }

    @Test
    fun givenFakeThreadRootExists_whenRestoringRootMessage_thenFakeRootIsPopulatedWithRestoredRoot() = runTest {
        val conversation = createTestConversation("restore-fake-root-conversation")
        val threadId = "restore-fake-root-id"
        val reply = createTestMessage(conversation.id, "restore-orphan-reply-id", userId)
        val restoredRoot = createTestMessage(
            conversationId = conversation.id,
            id = threadId,
            senderId = userId,
            content = MessageContent.Text("Restored root content"),
        )

        subject.insertUsers(listOf(createTestUser(userId.value)))
        subject.insertConversations(listOf(conversation))
        subject.insertMessages(listOf(reply))
        subject.insertThreadItems(
            listOf(
                BackupThreadItemData(conversation.id, reply.id, threadId, isRoot = false, reply.date)
            )
        )

        val fakeRoot = testDatabase.builder.messageDAO.getMessageById(threadId, conversation.id.toDao())
        assertIs<MessageEntity.Regular>(fakeRoot)
        assertIs<MessageEntityContent.MissingThreadRoot>(fakeRoot.content)

        subject.insertMessages(listOf(restoredRoot))
        subject.insertThreadRoots(
            listOf(
                BackupThreadRootData(
                    conversationId = conversation.id,
                    rootMessageId = restoredRoot.id,
                    threadId = threadId,
                    createdAt = restoredRoot.date,
                )
            )
        )
        subject.insertThreadItems(
            listOf(
                BackupThreadItemData(conversation.id, restoredRoot.id, threadId, isRoot = true, restoredRoot.date),
                BackupThreadItemData(conversation.id, reply.id, threadId, isRoot = false, reply.date),
            )
        )
        subject.refreshThreadMetadata(setOf(BackupThreadReference(conversation.id, threadId)))

        val populatedRoot = testDatabase.builder.messageDAO.getMessageById(threadId, conversation.id.toDao())
        val rootMapping = testDatabase.builder.messageThreadDAO.getThreadByRootMessage(conversation.id.toDao(), restoredRoot.id)
        val mainListIds = testDatabase.builder.messageDAO.getMessagesByConversationAndVisibility(
            conversationId = conversation.id.toDao(),
            limit = 50,
            offset = 0,
            visibility = listOf(MessageEntity.Visibility.VISIBLE)
        ).first().map { it.id }

        assertIs<MessageEntity.Regular>(populatedRoot)
        val rootContent = assertIs<MessageEntityContent.Text>(populatedRoot.content)
        assertEquals("Restored root content", rootContent.messageBody)
        assertEquals(restoredRoot.id, rootMapping?.rootMessageId)
        assertEquals(threadId, rootMapping?.threadId)
        assertContains(mainListIds, restoredRoot.id)
        assertFalse(mainListIds.contains(reply.id))
    }

    @Test
    fun givenFakeThreadRootExists_whenGettingThreadBackupData_thenFakeRootRowsAreExported() = runTest {
        val conversation = createTestConversation("export-fake-root-conversation")
        val threadId = "export-fake-root-id"
        val reply = createTestMessage(conversation.id, "export-fake-root-reply-id", userId)

        subject.insertUsers(listOf(createTestUser(userId.value)))
        subject.insertConversations(listOf(conversation))
        subject.insertMessages(listOf(reply))
        subject.insertThreadItems(
            listOf(
                BackupThreadItemData(conversation.id, reply.id, threadId, isRoot = false, reply.date)
            )
        )

        val exportedMessages = subject.getMessages().first().data.map { it.id }
        val exportedRoots = subject.getThreadRoots().first().data
        val exportedItems = subject.getThreadItems().first().data

        assertFalse(exportedMessages.contains(threadId))
        assertEquals(threadId, exportedRoots.single().rootMessageId)
        assertEquals(threadId, exportedRoots.single().threadId)
        assertContentEquals(listOf(threadId, reply.id), exportedItems.map { it.messageId })
    }

    @Test
    fun givenBackupContainsFakeThreadRoot_whenRestoringThreadData_thenPlaceholderKeepsBackedUpCreationDate() = runTest {
        val conversation = createTestConversation("restore-fake-root-order-conversation")
        val threadId = "restore-fake-root-order-id"
        val fakeRootDate = Instant.parse("2026-01-01T00:00:00Z")
        val replyDate = Instant.parse("2026-01-02T00:00:00Z")
        val mainMessageDate = Instant.parse("2026-01-03T00:00:00Z")
        val reply = createTestMessage(conversation.id, "restore-fake-root-order-reply-id", userId, date = replyDate)
        val mainMessage = createTestMessage(conversation.id, "restore-fake-root-order-main-id", userId, date = mainMessageDate)

        subject.insertUsers(listOf(createTestUser(userId.value)))
        subject.insertConversations(listOf(conversation))
        subject.insertMessages(listOf(reply, mainMessage))
        subject.insertThreadRoots(
            listOf(
                BackupThreadRootData(
                    conversationId = conversation.id,
                    rootMessageId = threadId,
                    threadId = threadId,
                    createdAt = fakeRootDate,
                )
            )
        )
        subject.insertThreadItems(
            listOf(
                BackupThreadItemData(conversation.id, threadId, threadId, isRoot = true, fakeRootDate),
                BackupThreadItemData(conversation.id, reply.id, threadId, isRoot = false, reply.date),
            )
        )
        subject.refreshThreadMetadata(setOf(BackupThreadReference(conversation.id, threadId)))

        val fakeRoot = testDatabase.builder.messageDAO.getMessageById(threadId, conversation.id.toDao())
        val rootMapping = testDatabase.builder.messageThreadDAO.getThreadByRootMessage(conversation.id.toDao(), threadId)
        val mainListIds = testDatabase.builder.messageDAO.getMessagesByConversationAndVisibility(
            conversationId = conversation.id.toDao(),
            limit = 50,
            offset = 0,
            visibility = listOf(MessageEntity.Visibility.VISIBLE)
        ).first().map { it.id }

        assertIs<MessageEntity.Regular>(fakeRoot)
        assertIs<MessageEntityContent.MissingThreadRoot>(fakeRoot.content)
        assertEquals(fakeRootDate, fakeRoot.date)
        assertEquals(fakeRootDate, rootMapping?.createdAt)
        assertEquals(1L, rootMapping?.visibleReplyCount)
        assertEquals(replyDate, rootMapping?.lastReplyDate)
        assertContentEquals(listOf(mainMessage.id, threadId), mainListIds)
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
        content: MessageContent.Regular = MessageContent.Text("Test"),
        date: Instant = TestMessage.TEXT_MESSAGE.date,
    ) = TestMessage.TEXT_MESSAGE.copy(
        conversationId = conversationId,
        id = id,
        senderUserId = senderId,
        content = content,
        date = date,
    )

    private fun createMultipartTestMessage(
        conversationId: QualifiedID,
        id: String,
        senderId: UserId,
    ) = TestMessage.multipartMessage().copy(
        conversationId = conversationId,
        id = id,
        senderUserId = senderId,
    )

    private fun createCompositeTestMessage(
        conversationId: QualifiedID,
        id: String,
        senderId: UserId,
    ) = TestMessage.TEXT_MESSAGE.copy(
        conversationId = conversationId,
        id = id,
        senderUserId = senderId,
        content = MessageContent.Composite(
            textContent = MessageContent.Text("composite backup content"),
            buttonList = listOf(Button(text = "Approve", id = "btn-1", isSelected = false)),
        )
    )

}
