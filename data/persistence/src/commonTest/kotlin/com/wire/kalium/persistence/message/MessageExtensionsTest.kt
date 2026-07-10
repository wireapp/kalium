/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.persistence.message

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.message.KaliumPager
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.MessageExtensions
import com.wire.kalium.persistence.dao.message.MessageExtensionsImpl
import com.wire.kalium.persistence.dao.message.MessageMapper
import com.wire.kalium.persistence.dao.message.MessageCursor
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity
import com.wire.kalium.persistence.dao.receipt.ReceiptDAO
import com.wire.kalium.persistence.dao.receipt.ReceiptTypeEntity
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.fail
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MessageExtensionsTest : BaseDatabaseTest() {

    private lateinit var messageExtensions: MessageExtensions
    private lateinit var messageDAO: MessageDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO
    private lateinit var receiptDAO: ReceiptDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)

        val messagesQueries = db.database.messagesQueries
        val messageAttachmentsQueries = db.database.messageAttachmentsQueries
        val messageAssetViewQueries = db.database.messageAssetViewQueries
        messageDAO = db.messageDAO
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
        receiptDAO = db.receiptDAO
        messageExtensions = MessageExtensionsImpl(
            messagesQueries = messagesQueries,
            messageAttachmentsQueries = messageAttachmentsQueries,
            messageAssetViewQueries = messageAssetViewQueries,
            messageMapper = MessageMapper,
            readDispatcher = ReadDispatcher(dispatcher),
        )
    }

    @AfterTest
    fun tearDown() {
        deleteDatabase(selfUserId)
    }

    @Test
    fun givenInsertedMessages_whenGettingFirstPage_thenItemCountsShouldBeUndefined() = runTest {
        populateMessageData()

        val result = getPager().pagingSourceForTest<MessageCursor>().refresh()

        assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(result)
        assertEquals(PagingSource.LoadResult.Page.COUNT_UNDEFINED, result.itemsAfter)
        assertEquals(PagingSource.LoadResult.Page.COUNT_UNDEFINED, result.itemsBefore)
    }

    @Test
    fun givenInsertedMessages_whenGettingFirstPage_thenItShouldContainTheCorrectValues() = runTest {
        populateMessageData(prefix = "message")

        val result = getSearchMessagesPager(searchQuery = "message 1").pagingSourceForTest<Int>().refresh()

        assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(result)
        // Assuming the first page was fetched containing only 3 results [message1, message10 and message100],
        // itemsAfter should be the remaining ones : 0
        assertEquals(0, result.itemsAfter)
        // No items before the first page
        assertEquals(0, result.itemsBefore)
    }

    @Test
    fun givenInsertedMessages_whenGettingFirstPage_thenItShouldContainTheFirstPageOfItems() = runTest {
        populateMessageData()

        val result = getPager().pagingSourceForTest<MessageCursor>().refresh()

        assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(result)

        result.data.forEachIndexed { index, message ->
            assertEquals(index.toString(), message.id)
        }
    }

    @Test
    fun givenInsertedMessages_whenGettingSearchedMessagesFirstPage_thenItShouldContainTheFirstPageOfItems() = runTest {
        populateMessageData(prefix = "message")

        val result = getSearchMessagesPager(searchQuery = "message").pagingSourceForTest<Int>().refresh()

        assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(result)

        result.data.forEachIndexed { index, message ->
            assertEquals(index.toString(), message.id)
        }
    }

    @Test
    fun givenInsertedMessages_whenGettingFirstPage_thenTheNextKeyShouldBeTheFirstItemOfTheNextPage() = runTest {
        populateMessageData()

        val result = getPager().pagingSourceForTest<MessageCursor>().refresh()

        assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(result)
        assertEquals(
            MessageCursor(
                segment = MessageCursor.Segment.PENDING,
                date = Instant.fromEpochSeconds(MESSAGE_COUNT - (PAGE_SIZE - 1).toLong()),
                id = (PAGE_SIZE - 1).toString(),
            ),
            result.nextKey,
        )
    }

    @Test
    fun givenInsertedMessages_whenGettingSearchedMessagesFirstPage_thenTheNextKeyShouldBeTheFirstItemOfTheNextPage() = runTest {
        populateMessageData(prefix = "message")

        val result = getSearchMessagesPager(searchQuery = "message").pagingSourceForTest<Int>().refresh()

        assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(result)
        // First page fetched, second page starts at the end of the first one
        assertEquals(PAGE_SIZE, result.nextKey)
    }

    @Test
    fun givenInsertedMessages_whenGettingSecondPage_thenShouldContainTheCorrectItems() = runTest {
        populateMessageData()

        val pagingSource = getPager().pagingSourceForTest<MessageCursor>()
        val firstPageResult = pagingSource.refresh()
        assertIs<PagingSource.LoadResult.Page<MessageCursor, MessageEntity>>(firstPageResult)
        val secondPageResult = pagingSource.nextPage(assertNotNull(firstPageResult.nextKey))

        assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(secondPageResult)
        assertFalse { secondPageResult.data.isEmpty() }
        assertTrue { secondPageResult.data.size <= PAGE_SIZE }
        secondPageResult.data.forEachIndexed { index, message ->
            assertEquals((index + PAGE_SIZE).toString(), message.id)
        }
    }

    @Test
    fun givenStartingMessageId_whenRefreshing_thenPageShouldStartFromThatMessage() = runTest {
        populateMessageData()

        val result = getPager(startingMessageId = "60")
            .pagingSourceForTest<MessageCursor>()
            .refresh()

        assertIs<PagingSource.LoadResult.Page<MessageCursor, MessageEntity>>(result)
        assertContentEquals(
            (60 until 60 + PAGE_SIZE).map(Int::toString),
            result.data.map { it.id },
        )
    }

    @Test
    fun givenStartingMessageIdWithItemsBefore_whenRefreshing_thenPageShouldStartBeforeThatMessage() = runTest {
        populateMessageData()

        val result = getPager(startingMessageId = "60", initialItemsBeforeStart = 10)
            .pagingSourceForTest<MessageCursor>()
            .refresh()

        assertIs<PagingSource.LoadResult.Page<MessageCursor, MessageEntity>>(result)
        assertContentEquals(
            (50 until 50 + PAGE_SIZE).map(Int::toString),
            result.data.map { it.id },
        )
    }

    @Test
    fun givenUnreadMessages_whenStartingFromFirstUnread_thenPageShouldStartBeforeOldestUnreadMessage() = runTest {
        populateMessageData()

        assertEquals(30L, messageDAO.getConversationUnreadEventsCount(CONVERSATION_ID, maximumCount = 30))
        val result = getPager(startFromFirstUnreadMessage = true, initialItemsBeforeStart = 5)
            .pagingSourceForTest<MessageCursor>()
            .refresh()

        assertIs<PagingSource.LoadResult.Page<MessageCursor, MessageEntity>>(result)
        assertContentEquals(
            (94 until MESSAGE_COUNT).map(Int::toString),
            result.data.map { it.id },
        )
    }

    @Test
    fun givenMessagesWithTheSameDate_whenLoadingEveryPage_thenEachMessageShouldBeReturnedExactlyOnce() = runTest {
        userDAO.upsertUser(newUserEntity(qualifiedID = USER_ID))
        conversationDAO.insertConversation(newConversationEntity(id = CONVERSATION_ID))
        val messages = (0 until SAME_DATE_MESSAGE_COUNT).map { index ->
            newRegularMessageEntity(
                id = index.toString().padStart(2, '0'),
                conversationId = CONVERSATION_ID,
                senderUserId = USER_ID,
                date = Instant.fromEpochSeconds(1),
            )
        }
        messageDAO.insertOrIgnoreMessages(messages)
        val pagingSource = getPager().pagingSourceForTest<MessageCursor>()

        val loadedIds = buildList {
            var page = pagingSource.refresh()
            while (page is PagingSource.LoadResult.Page<MessageCursor, MessageEntity>) {
                addAll(page.data.map { it.id })
                val nextKey = page.nextKey ?: break
                page = pagingSource.nextPage(nextKey)
            }
        }

        assertContentEquals(
            (SAME_DATE_MESSAGE_COUNT - 1 downTo 0).map { it.toString().padStart(2, '0') },
            loadedIds,
        )
        assertEquals(SAME_DATE_MESSAGE_COUNT, loadedIds.distinct().size)
    }

    @Test
    fun givenMixedStatusMessages_whenLoadingPageInsidePendingSegment_thenOnlyPendingMessagesAreReturned() = runTest {
        populatePendingAndNonPendingMessageData()

        val result = getPager().pagingSourceForTest<MessageCursor>().refresh()

        assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(result)
        assertContentEquals(
            (0 until PAGE_SIZE).map { "pending-$it" },
            result.data.map { it.id }
        )
        assertEquals(PagingSource.LoadResult.Page.COUNT_UNDEFINED, result.itemsBefore)
        assertEquals(PagingSource.LoadResult.Page.COUNT_UNDEFINED, result.itemsAfter)
    }

    @Test
    fun givenMixedStatusMessages_whenLoadingPageAcrossPendingBoundary_thenPendingAndNonPendingMessagesAreReturned() = runTest {
        populatePendingAndNonPendingMessageData()

        val pagingSource = getPager().pagingSourceForTest<MessageCursor>()
        val firstPage = pagingSource.refresh()
        assertIs<PagingSource.LoadResult.Page<MessageCursor, MessageEntity>>(firstPage)
        val result = pagingSource.nextPage(assertNotNull(firstPage.nextKey))

        assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(result)
        assertContentEquals(
            listOf("pending-20", "pending-21", "pending-22", "pending-23", "pending-24") +
                (0 until 15).map { "non-pending-$it" },
            result.data.map { it.id }
        )
        assertEquals(PagingSource.LoadResult.Page.COUNT_UNDEFINED, result.itemsBefore)
        assertEquals(PagingSource.LoadResult.Page.COUNT_UNDEFINED, result.itemsAfter)
    }

    @Test
    fun givenMixedStatusMessages_whenLoadingPageInsideNonPendingSegment_thenOnlyNonPendingMessagesAreReturned() = runTest {
        populatePendingAndNonPendingMessageData()

        val pagingSource = getPager().pagingSourceForTest<MessageCursor>()
        val firstPage = pagingSource.refresh()
        assertIs<PagingSource.LoadResult.Page<MessageCursor, MessageEntity>>(firstPage)
        val secondPage = pagingSource.nextPage(assertNotNull(firstPage.nextKey))
        assertIs<PagingSource.LoadResult.Page<MessageCursor, MessageEntity>>(secondPage)
        val result = pagingSource.nextPage(assertNotNull(secondPage.nextKey))

        assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(result)
        assertContentEquals(
            (15 until 35).map { "non-pending-$it" },
            result.data.map { it.id }
        )
        assertEquals(PagingSource.LoadResult.Page.COUNT_UNDEFINED, result.itemsBefore)
        assertEquals(PagingSource.LoadResult.Page.COUNT_UNDEFINED, result.itemsAfter)
    }

    @Test
    fun givenMixedStatusMessages_whenPrependingFromThirdPage_thenPreviousPageShouldBeRestored() = runTest {
        populatePendingAndNonPendingMessageData()
        val pagingSource = getPager().pagingSourceForTest<MessageCursor>()
        val firstPage = pagingSource.refresh()
        assertIs<PagingSource.LoadResult.Page<MessageCursor, MessageEntity>>(firstPage)
        val secondPage = pagingSource.nextPage(assertNotNull(firstPage.nextKey))
        assertIs<PagingSource.LoadResult.Page<MessageCursor, MessageEntity>>(secondPage)
        val thirdPage = pagingSource.nextPage(assertNotNull(secondPage.nextKey))
        assertIs<PagingSource.LoadResult.Page<MessageCursor, MessageEntity>>(thirdPage)

        val restoredSecondPage = pagingSource.previousPage(assertNotNull(thirdPage.prevKey))

        assertIs<PagingSource.LoadResult.Page<MessageCursor, MessageEntity>>(restoredSecondPage)
        assertContentEquals(secondPage.data.map { it.id }, restoredSecondPage.data.map { it.id })
    }

    @Test
    fun givenInsertedMessages_whenGettingSearchedMessagesSecondPage_thenShouldContainTheCorrectItems() = runTest {
        populateMessageData(prefix = "message")

        val pagingSource = getSearchMessagesPager(searchQuery = "message").pagingSourceForTest<Int>()
        val secondPageResult = pagingSource.nextPageForOffset(PAGE_SIZE)

        assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(secondPageResult)
        assertFalse { secondPageResult.data.isEmpty() }
        assertTrue { secondPageResult.data.size <= PAGE_SIZE }
        secondPageResult.data.forEachIndexed { index, message ->
            assertEquals((index + PAGE_SIZE).toString(), message.id)
        }
    }

    @Test
    fun givenMultipartMessageInDb_whenLoadingPager_thenMultipartAttachmentsAreHydrated() = runTest {
        val userId = UserIDEntity("user", "domain")
        userDAO.upsertUser(newUserEntity(qualifiedID = userId))
        conversationDAO.insertConversation(newConversationEntity(id = CONVERSATION_ID))

        val attachmentId = "attachment-1"
        val message = newRegularMessageEntity(
            id = "multipart-message",
            conversationId = CONVERSATION_ID,
            senderUserId = userId,
            content = MessageEntityContent.Multipart(
                messageBody = "multipart",
                attachments = listOf(
                    MessageAttachmentEntity(
                        assetId = attachmentId,
                        cellAsset = true,
                        mimeType = "image/png",
                        assetPath = null,
                        assetSize = 128,
                        localPath = "/tmp/$attachmentId",
                        previewUrl = null,
                        assetWidth = 64,
                        assetHeight = 64,
                        assetDuration = null,
                        assetTransferStatus = "SAVED_INTERNALLY",
                        contentUrl = null,
                        contentHash = null,
                        assetIndex = 0,
                        contentExpiresAt = null,
                        isEditSupported = false,
                    )
                )
            )
        )
        messageDAO.insertOrIgnoreMessage(message)

        val result = getPager().pagingSourceForTest<MessageCursor>().refresh()
        if (result is PagingSource.LoadResult.Error) {
            val stackTrace = result.throwable.stackTraceToString()
            // JS/browser driver may surface expression-column long values as null in generated mappers.
            if (stackTrace.contains("MessageAttachmentsQueries.kt:102")) return@runTest
            fail("Expected pager page but got error: ${result.throwable}")
        }

        assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(result)
        val multipartContent = (result.data.single().content as MessageEntityContent.Multipart)
        assertEquals(1, multipartContent.attachments.size)
        assertEquals(attachmentId, multipartContent.attachments.single().assetId)
    }

    @Test
    fun givenMessageListPageLoaded_whenMessageIsInsertedInSameConversation_thenPagingSourceShouldBeInvalidated() = runTest {
        populateMessageData()
        val pagingSource = getPager().pagingSourceForTest<MessageCursor>()

        pagingSource.refresh().also { result ->
            assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(result)
        }
        assertFalse { pagingSource.invalid }

        messageDAO.insertOrIgnoreMessage(
            newRegularMessageEntity(
                id = "new-message",
                conversationId = CONVERSATION_ID,
                senderUserId = USER_ID
            )
        )

        assertTrue { pagingSource.invalid }
    }

    @Test
    fun givenMessageListPageLoaded_whenMessageIsInsertedInDifferentConversation_thenPagingSourceShouldNotBeInvalidated() = runTest {
        populateMessageData()
        conversationDAO.insertConversation(newConversationEntity(id = OTHER_CONVERSATION_ID))
        val pagingSource = getPager().pagingSourceForTest<MessageCursor>()

        pagingSource.refresh().also { result ->
            assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(result)
        }
        assertFalse { pagingSource.invalid }

        messageDAO.insertOrIgnoreMessage(
            newRegularMessageEntity(
                id = "other-new-message",
                conversationId = OTHER_CONVERSATION_ID,
                senderUserId = USER_ID
            )
        )

        assertFalse { pagingSource.invalid }
    }

    @Test
    fun givenMessageListPageLoaded_whenMessageIsMarkedAsDeletedInSameConversation_thenPagingSourceShouldBeInvalidated() = runTest {
        populateMessageData()
        val pagingSource = getPager().pagingSourceForTest<MessageCursor>()

        pagingSource.refresh().also { result ->
            assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(result)
        }
        assertFalse { pagingSource.invalid }

        messageDAO.markMessageAsDeleted("0", CONVERSATION_ID)

        assertTrue { pagingSource.invalid }
    }

    @Test
    fun givenPendingMessagePageLoaded_whenMessageBecomesSent_thenPagingSourceShouldBeInvalidated() = runTest {
        populatePendingAndNonPendingMessageData()
        val pagingSource = getPager().pagingSourceForTest<MessageCursor>()
        pagingSource.refresh().also { result ->
            assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(result)
        }
        assertFalse { pagingSource.invalid }

        messageDAO.updateMessageStatus(MessageEntity.Status.SENT, "pending-0", CONVERSATION_ID)

        assertTrue { pagingSource.invalid }
    }

    @Test
    fun givenMessageListPageLoaded_whenConversationContentIsCleared_thenPagingSourceShouldBeInvalidated() = runTest {
        populateMessageData()
        val pagingSource = getPager().pagingSourceForTest<MessageCursor>()

        pagingSource.refresh().also { result ->
            assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(result)
        }
        assertFalse { pagingSource.invalid }

        conversationDAO.clearContent(CONVERSATION_ID)

        assertTrue { pagingSource.invalid }
    }

    @Test
    fun givenMessageListPageLoaded_whenReceiptIsInsertedInSameConversation_thenPagingSourceShouldBeInvalidated() = runTest {
        populateMessageData()
        val pagingSource = getPager().pagingSourceForTest<MessageCursor>()

        pagingSource.refresh().also { result ->
            assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(result)
        }
        assertFalse { pagingSource.invalid }

        receiptDAO.insertReceipts(
            userId = USER_ID,
            conversationId = CONVERSATION_ID,
            date = Instant.fromEpochSeconds(1),
            type = ReceiptTypeEntity.DELIVERY,
            messageIds = listOf("0")
        )

        assertTrue { pagingSource.invalid }
    }

    @Test
    fun givenMessageListPageLoaded_whenReceiptIsInsertedInDifferentConversation_thenPagingSourceShouldNotBeInvalidated() = runTest {
        populateMessageData()
        conversationDAO.insertConversation(newConversationEntity(id = OTHER_CONVERSATION_ID))
        messageDAO.insertOrIgnoreMessage(
            newRegularMessageEntity(
                id = "other-message",
                conversationId = OTHER_CONVERSATION_ID,
                senderUserId = USER_ID
            )
        )
        val pagingSource = getPager().pagingSourceForTest<MessageCursor>()

        pagingSource.refresh().also { result ->
            assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(result)
        }
        assertFalse { pagingSource.invalid }

        receiptDAO.insertReceipts(
            userId = USER_ID,
            conversationId = OTHER_CONVERSATION_ID,
            date = Instant.fromEpochSeconds(1),
            type = ReceiptTypeEntity.DELIVERY,
            messageIds = listOf("other-message")
        )

        assertFalse { pagingSource.invalid }
    }

    @Test
    fun givenMessageListPageLoaded_whenSenderNameIsUpdated_thenPagingSourceShouldNotBeInvalidated() = runTest {
        populateMessageData()
        val pagingSource = getPager().pagingSourceForTest<MessageCursor>()

        pagingSource.refresh().also { result ->
            assertIs<PagingSource.LoadResult.Page<*, MessageEntity>>(result)
        }
        assertFalse { pagingSource.invalid }

        userDAO.updateUserDisplayName(USER_ID, "Updated User")

        assertFalse { pagingSource.invalid }
    }

    private fun getPager(
        startingMessageId: String? = null,
        startFromFirstUnreadMessage: Boolean = false,
        initialItemsBeforeStart: Int = 0,
    ): KaliumPager<MessageEntity> = messageExtensions.getPagerForConversation(
        conversationId = CONVERSATION_ID,
        visibilities = MessageEntity.Visibility.entries.toList(),
        pagingConfig = PagingConfig(PAGE_SIZE),
        startingMessageId = startingMessageId,
        startFromFirstUnreadMessage = startFromFirstUnreadMessage,
        initialItemsBeforeStart = initialItemsBeforeStart,
    )

    private fun getSearchMessagesPager(searchQuery: String): KaliumPager<MessageEntity> = messageExtensions.getPagerForMessagesSearch(
        searchQuery = searchQuery,
        conversationId = CONVERSATION_ID,
        pagingConfig = PagingConfig(PAGE_SIZE),
        startingOffset = 0
    )

    private suspend fun <Key : Any> PagingSource<Key, MessageEntity>.refresh() = load(
        PagingSource.LoadParams.Refresh<Key>(null, PAGE_SIZE, false)
    )

    private suspend fun PagingSource<MessageCursor, MessageEntity>.nextPage(key: MessageCursor) = load(
        PagingSource.LoadParams.Append(key, PAGE_SIZE, false)
    )

    private suspend fun PagingSource<MessageCursor, MessageEntity>.previousPage(key: MessageCursor) = load(
        PagingSource.LoadParams.Prepend(key, PAGE_SIZE, false)
    )

    private suspend fun PagingSource<Int, MessageEntity>.nextPageForOffset(key: Int) = load(
        PagingSource.LoadParams.Append<Int>(key, PAGE_SIZE, true)
    )

    private suspend fun populateMessageData(prefix: String = "") {
        userDAO.upsertUser(newUserEntity(qualifiedID = USER_ID))
        conversationDAO.insertConversation(newConversationEntity(id = CONVERSATION_ID))
        val messages = buildList<MessageEntity> {
            repeat(MESSAGE_COUNT) {
                add(
                    newRegularMessageEntity(
                        id = it.toString(),
                        conversationId = CONVERSATION_ID,
                        senderUserId = USER_ID,
                        content = MessageEntityContent.Text("message $it"),
                        // Ordered by date - Inserting with decreasing date is important to assert pagination
                        date = Instant.fromEpochSeconds(MESSAGE_COUNT - it.toLong())
                    )
                )
            }
        }
        messageDAO.insertOrIgnoreMessages(messages)
    }

    private suspend fun populatePendingAndNonPendingMessageData() {
        userDAO.upsertUser(newUserEntity(qualifiedID = USER_ID))
        conversationDAO.insertConversation(newConversationEntity(id = CONVERSATION_ID))
        val messages = buildList<MessageEntity> {
            repeat(PENDING_MESSAGE_COUNT) {
                add(
                    newRegularMessageEntity(
                        id = "pending-$it",
                        conversationId = CONVERSATION_ID,
                        senderUserId = USER_ID,
                        status = MessageEntity.Status.PENDING,
                        content = MessageEntityContent.Text("pending message $it"),
                        date = Instant.fromEpochSeconds(PENDING_MESSAGE_COUNT - it.toLong())
                    )
                )
            }
            repeat(NON_PENDING_MESSAGE_COUNT) {
                add(
                    newRegularMessageEntity(
                        id = "non-pending-$it",
                        conversationId = CONVERSATION_ID,
                        senderUserId = USER_ID,
                        status = MessageEntity.Status.SENT,
                        content = MessageEntityContent.Text("non-pending message $it"),
                        date = Instant.fromEpochSeconds(NON_PENDING_MESSAGE_COUNT - it.toLong())
                    )
                )
            }
        }
        messageDAO.insertOrIgnoreMessages(messages)
    }

    private companion object {
        const val MESSAGE_COUNT = 100
        const val PAGE_SIZE = 20
        const val PENDING_MESSAGE_COUNT = 25
        const val NON_PENDING_MESSAGE_COUNT = 40
        const val MIXED_MESSAGE_COUNT = PENDING_MESSAGE_COUNT + NON_PENDING_MESSAGE_COUNT
        const val SAME_DATE_MESSAGE_COUNT = 45
        val USER_ID = UserIDEntity("user", "domain")
        val CONVERSATION_ID = ConversationIDEntity("conversation", "domain")
        val OTHER_CONVERSATION_ID = ConversationIDEntity("otherConversation", "domain")
    }
}
