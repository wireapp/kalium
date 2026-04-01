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

import app.cash.paging.PagingConfig
import app.cash.paging.PagingSource
import app.cash.paging.PagingSourceLoadParamsAppend
import app.cash.paging.PagingSourceLoadParamsRefresh
import app.cash.paging.PagingSourceLoadResultPage
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
import com.wire.kalium.persistence.dao.reaction.ReactionDAO
import com.wire.kalium.persistence.dao.receipt.ReceiptDAO
import com.wire.kalium.persistence.dao.receipt.ReceiptTypeEntity
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.WriteDispatcher
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MessageExtensionsTest : BaseDatabaseTest() {

    private lateinit var databaseBuilder: UserDatabaseBuilder
    private lateinit var messageExtensions: MessageExtensions
    private lateinit var messageDAO: MessageDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO
    private lateinit var reactionDAO: ReactionDAO
    private lateinit var receiptDAO: ReceiptDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")
    private val otherUserId = UserIDEntity("user", "domain")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deleteDatabase(selfUserId)
        databaseBuilder = createDatabase(selfUserId, encryptedDBSecret, true)
        val db = databaseBuilder

        val messagesQueries = db.database.messagesQueries
        val messageAssetViewQueries = db.database.messageAssetViewQueries
        messageDAO = db.messageDAO
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
        reactionDAO = db.reactionDAO
        receiptDAO = db.receiptDAO
        messageExtensions = MessageExtensionsImpl(
            messagesQueries = messagesQueries,
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
    fun givenInsertedMessages_whenGettingFirstPage_thenItShouldContainTheCorrectCountBeforeAndAfter() = runTest {
        populateMessageData()

        val result = getPager().pagingSource.refresh()

        assertIs<PagingSourceLoadResultPage<Int, MessageEntity>>(result)
        // Assuming the first page was fetched, itemsAfter should be the remaining ones
        assertEquals(MESSAGE_COUNT - PAGE_SIZE, result.itemsAfter)
        // No items before the first page
        assertEquals(0, result.itemsBefore)
    }

    @Test
    fun givenInsertedMessages_whenGettingFirstPage_thenItShouldContainTheCorrectValues() = runTest {
        populateMessageData(prefix = "message")

        val result = getSearchMessagesPager(searchQuery = "message 1").pagingSource.refresh()

        assertIs<PagingSourceLoadResultPage<Int, MessageEntity>>(result)
        // Assuming the first page was fetched containing only 3 results [message1, message10 and message100],
        // itemsAfter should be the remaining ones : 0
        assertEquals(0, result.itemsAfter)
        // No items before the first page
        assertEquals(0, result.itemsBefore)
    }

    @Test
    fun givenInsertedMessages_whenGettingFirstPage_thenItShouldContainTheFirstPageOfItems() = runTest {
        populateMessageData()

        val result = getPager().pagingSource.refresh()

        assertIs<PagingSourceLoadResultPage<Long, MessageEntity>>(result)

        result.data.forEachIndexed { index, message ->
            assertEquals(index.toString(), message.id)
        }
    }

    @Test
    fun givenInsertedMessages_whenGettingSearchedMessagesFirstPage_thenItShouldContainTheFirstPageOfItems() = runTest {
        populateMessageData(prefix = "message")

        val result = getSearchMessagesPager(searchQuery = "message").pagingSource.refresh()

        assertIs<PagingSourceLoadResultPage<Long, MessageEntity>>(result)

        result.data.forEachIndexed { index, message ->
            assertEquals(index.toString(), message.id)
        }
    }

    @Test
    fun givenInsertedMessages_whenGettingFirstPage_thenTheNextKeyShouldBeTheFirstItemOfTheNextPage() = runTest {
        populateMessageData()

        val result = getPager().pagingSource.refresh()

        assertIs<PagingSourceLoadResultPage<Int, MessageEntity>>(result)
        // First page fetched, second page starts at the end of the first one
        assertEquals(PAGE_SIZE, result.nextKey)
    }

    @Test
    fun givenInsertedMessages_whenGettingSearchedMessagesFirstPage_thenTheNextKeyShouldBeTheFirstItemOfTheNextPage() = runTest {
        populateMessageData(prefix = "message")

        val result = getSearchMessagesPager(searchQuery = "message").pagingSource.refresh()

        assertIs<PagingSourceLoadResultPage<Int, MessageEntity>>(result)
        // First page fetched, second page starts at the end of the first one
        assertEquals(PAGE_SIZE, result.nextKey)
    }

    @Test
    fun givenInsertedMessages_whenGettingSecondPage_thenShouldContainTheCorrectItems() = runTest {
        populateMessageData()

        val pagingSource = getPager().pagingSource
        val secondPageResult = pagingSource.nextPageForOffset(PAGE_SIZE)

        assertIs<PagingSourceLoadResultPage<Int, MessageEntity>>(secondPageResult)
        assertFalse { secondPageResult.data.isEmpty() }
        assertTrue { secondPageResult.data.size <= PAGE_SIZE }
        secondPageResult.data.forEachIndexed { index, message ->
            assertEquals((index + PAGE_SIZE).toString(), message.id)
        }
    }

    @Test
    fun givenInsertedMessages_whenGettingSearchedMessagesSecondPage_thenShouldContainTheCorrectItems() = runTest {
        populateMessageData(prefix = "message")

        val pagingSource = getSearchMessagesPager(searchQuery = "message").pagingSource
        val secondPageResult = pagingSource.nextPageForOffset(PAGE_SIZE)

        assertIs<PagingSourceLoadResultPage<Int, MessageEntity>>(secondPageResult)
        assertFalse { secondPageResult.data.isEmpty() }
        assertTrue { secondPageResult.data.size <= PAGE_SIZE }
        secondPageResult.data.forEachIndexed { index, message ->
            assertEquals((index + PAGE_SIZE).toString(), message.id)
        }
    }

    @Test
    fun givenMessageListPagingSource_whenMessageIsInsertedInSameConversation_thenItInvalidates() = runTest {
        populateMessageData()
        val pagingSource = getPager().pagingSource

        pagingSource.refresh()
        val invalidated = pagingSource.observeInvalidation()

        messageDAO.insertOrIgnoreMessage(
            newRegularMessageEntity(
                id = "message_after_load",
                conversationId = CONVERSATION_ID,
                senderUserId = otherUserId,
                date = Instant.parse("2024-01-01T00:00:00Z"),
            )
        )

        advanceUntilIdle()

        assertTrue(invalidated())
    }

    @Test
    fun givenMessageListPagingSource_whenMessageIsInsertedInOtherConversation_thenItDoesNotInvalidate() = runTest {
        populateMessageData()
        val pagingSource = getPager().pagingSource

        pagingSource.refresh()
        val invalidated = pagingSource.observeInvalidation()

        val otherConversationId = ConversationIDEntity("other-conversation", "domain")
        conversationDAO.insertConversation(newConversationEntity(id = otherConversationId))
        messageDAO.insertOrIgnoreMessage(
            newRegularMessageEntity(
                id = "other_conversation_message",
                conversationId = otherConversationId,
                senderUserId = otherUserId,
                date = Instant.parse("2024-01-01T00:00:00Z"),
            )
        )

        advanceUntilIdle()

        assertFalse(invalidated())
    }

    @Test
    fun givenMessageListPagingSource_whenReactionChangesInSameConversation_thenItInvalidates() = runTest {
        populateMessageData()
        val pagingSource = getPager().pagingSource

        pagingSource.refresh()
        val invalidated = pagingSource.observeInvalidation()

        reactionDAO.insertReaction(
            originalMessageId = "0",
            conversationId = CONVERSATION_ID,
            senderUserId = otherUserId,
            instant = Instant.parse("2024-01-01T00:00:00Z"),
            emoji = "🔥"
        )

        advanceUntilIdle()

        assertTrue(invalidated())
    }

    @Test
    fun givenMessageListPagingSource_whenReceiptChangesInSameConversation_thenItInvalidates() = runTest {
        populateMessageData()
        val pagingSource = getPager().pagingSource

        pagingSource.refresh()
        val invalidated = pagingSource.observeInvalidation()

        receiptDAO.insertReceipts(
            userId = otherUserId,
            conversationId = CONVERSATION_ID,
            date = Instant.parse("2024-01-01T00:00:00Z"),
            type = ReceiptTypeEntity.READ,
            messageIds = listOf("0"),
        )

        advanceUntilIdle()

        assertTrue(invalidated())
    }

    @Test
    fun givenMessageListPagingSource_whenTextMessageIsEditedInSameConversation_thenItInvalidates() = runTest {
        populateMessageData()
        val pagingSource = getPager().pagingSource

        pagingSource.refresh()
        val invalidated = pagingSource.observeInvalidation()

        messageDAO.updateTextMessageContent(
            editInstant = Instant.parse("2024-01-01T00:00:00Z"),
            conversationId = CONVERSATION_ID,
            currentMessageId = "0",
            newTextContent = MessageEntityContent.Text("edited message"),
            newMessageId = "0-edited"
        )

        advanceUntilIdle()

        assertTrue(invalidated())
    }

    @Test
    fun givenMessageListPagingSource_whenUnrelatedMetadataChanges_thenItDoesNotInvalidate() = runTest {
        populateMessageData()
        val pagingSource = getPager().pagingSource

        pagingSource.refresh()
        val invalidated = pagingSource.observeInvalidation()

        databaseBuilder.database.metadataQueries.insertValue("some_key", "some_value")

        advanceUntilIdle()

        assertFalse(invalidated())
    }

    @Test
    fun givenMessageSearchPagingSource_whenMatchingMessageIsInserted_thenItInvalidates() = runTest {
        populateMessageData()
        val pagingSource = getSearchMessagesPager(searchQuery = "needle").pagingSource

        pagingSource.refresh().also { result ->
            assertIs<PagingSourceLoadResultPage<Int, MessageEntity>>(result)
            assertTrue(result.data.isEmpty())
        }
        val invalidated = pagingSource.observeInvalidation()

        messageDAO.insertOrIgnoreMessage(
            newRegularMessageEntity(
                id = "search_message_after_load",
                conversationId = CONVERSATION_ID,
                senderUserId = otherUserId,
                date = Instant.parse("2024-01-01T00:00:00Z"),
                content = MessageEntityContent.Text("contains needle")
            )
        )

        advanceUntilIdle()

        assertTrue(invalidated())
        getSearchMessagesPager(searchQuery = "needle").pagingSource.refresh().also { result ->
            assertIs<PagingSourceLoadResultPage<Int, MessageEntity>>(result)
            assertEquals(listOf("search_message_after_load"), result.data.map { it.id })
        }
    }

    @Test
    fun givenMessageSearchPagingSource_whenTextMessageIsEditedToMatch_thenItInvalidates() = runTest {
        populateMessageData()
        val pagingSource = getSearchMessagesPager(searchQuery = "edited").pagingSource

        pagingSource.refresh().also { result ->
            assertIs<PagingSourceLoadResultPage<Int, MessageEntity>>(result)
            assertTrue(result.data.isEmpty())
        }
        val invalidated = pagingSource.observeInvalidation()

        messageDAO.updateTextMessageContent(
            editInstant = Instant.parse("2024-01-01T00:00:00Z"),
            conversationId = CONVERSATION_ID,
            currentMessageId = "0",
            newTextContent = MessageEntityContent.Text("edited message"),
            newMessageId = "0-edited-search"
        )

        advanceUntilIdle()

        assertTrue(invalidated())
        getSearchMessagesPager(searchQuery = "edited").pagingSource.refresh().also { result ->
            assertIs<PagingSourceLoadResultPage<Int, MessageEntity>>(result)
            assertEquals(listOf("0-edited-search"), result.data.map { it.id })
        }
    }

    @Test
    fun givenMessageSearchPagingSource_whenMatchingMessageIsInsertedInOtherConversation_thenItDoesNotInvalidateAndResultsStayEmpty() = runTest {
        populateMessageData()
        val pagingSource = getSearchMessagesPager(searchQuery = "needle").pagingSource

        pagingSource.refresh().also { result ->
            assertIs<PagingSourceLoadResultPage<Int, MessageEntity>>(result)
            assertTrue(result.data.isEmpty())
        }
        val invalidated = pagingSource.observeInvalidation()

        val otherConversationId = ConversationIDEntity("other-search-conversation", "domain")
        conversationDAO.insertConversation(newConversationEntity(id = otherConversationId))
        messageDAO.insertOrIgnoreMessage(
            newRegularMessageEntity(
                id = "other_search_message",
                conversationId = otherConversationId,
                senderUserId = otherUserId,
                date = Instant.parse("2024-01-01T00:00:00Z"),
                content = MessageEntityContent.Text("contains needle")
            )
        )

        advanceUntilIdle()

        assertFalse(invalidated())
        getSearchMessagesPager(searchQuery = "needle").pagingSource.refresh().also { result ->
            assertIs<PagingSourceLoadResultPage<Int, MessageEntity>>(result)
            assertTrue(result.data.isEmpty())
        }
    }

    @Test
    fun givenMessageSearchPagingSource_whenUnrelatedMetadataChanges_thenItDoesNotInvalidate() = runTest {
        populateMessageData()
        val pagingSource = getSearchMessagesPager(searchQuery = "message").pagingSource

        pagingSource.refresh()
        val invalidated = pagingSource.observeInvalidation()

        databaseBuilder.database.metadataQueries.insertValue("search_key", "search_value")

        advanceUntilIdle()

        assertFalse(invalidated())
    }

    private fun getPager(): KaliumPager<MessageEntity> = messageExtensions.getPagerForConversation(
        conversationId = CONVERSATION_ID,
        visibilities = MessageEntity.Visibility.entries.toList(),
        pagingConfig = PagingConfig(PAGE_SIZE),
        startingOffset = 0
    )

    private fun getSearchMessagesPager(searchQuery: String): KaliumPager<MessageEntity> = messageExtensions.getPagerForMessagesSearch(
        searchQuery = searchQuery,
        conversationId = CONVERSATION_ID,
        pagingConfig = PagingConfig(PAGE_SIZE),
        startingOffset = 0
    )

    private suspend fun PagingSource<Int, MessageEntity>.refresh() = load(
        PagingSourceLoadParamsRefresh<Int>(null, PAGE_SIZE, false)
    )

    private suspend fun PagingSource<Int, MessageEntity>.nextPageForOffset(key: Int) = load(
        PagingSourceLoadParamsAppend<Int>(key, PAGE_SIZE, true)
    )

    private fun PagingSource<Int, MessageEntity>.observeInvalidation(): () -> Boolean {
        var invalidated = false
        registerInvalidatedCallback {
            invalidated = true
        }
        return { invalidated }
    }

    private suspend fun populateMessageData(prefix: String = "") {
        userDAO.upsertUser(newUserEntity(qualifiedID = otherUserId))
        conversationDAO.insertConversation(newConversationEntity(id = CONVERSATION_ID))
        val messages = buildList<MessageEntity> {
            repeat(MESSAGE_COUNT) {
                add(
                    newRegularMessageEntity(
                        id = it.toString(),
                        conversationId = CONVERSATION_ID,
                        senderUserId = otherUserId,
                        content = MessageEntityContent.Text("message $it"),
                        // Ordered by date - Inserting with decreasing date is important to assert pagination
                        date = Instant.fromEpochSeconds(MESSAGE_COUNT - it.toLong())
                    )
                )
            }
        }
        messageDAO.insertOrIgnoreMessages(messages)
    }

    private companion object {
        const val MESSAGE_COUNT = 100
        const val PAGE_SIZE = 20
        val CONVERSATION_ID = ConversationIDEntity("conversation", "domain")
    }
}
