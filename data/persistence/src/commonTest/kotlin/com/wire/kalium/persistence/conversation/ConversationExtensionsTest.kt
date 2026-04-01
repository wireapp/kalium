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

package com.wire.kalium.persistence.conversation

import app.cash.paging.PagingConfig
import app.cash.paging.PagingSource
import app.cash.paging.PagingSourceLoadParamsAppend
import app.cash.paging.PagingSourceLoadParamsRefresh
import app.cash.paging.PagingSourceLoadResultPage
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.ConnectionDAO
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsMapper
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationExtensions
import com.wire.kalium.persistence.dao.conversation.ConversationExtensionsImpl
import com.wire.kalium.persistence.dao.conversation.ConversationFilterEntity
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.persistence.dao.message.KaliumPager
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.draft.MessageDraftDAO
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newDraftMessageEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ConversationExtensionsTest : BaseDatabaseTest() {
    private lateinit var databaseBuilder: UserDatabaseBuilder
    private lateinit var conversationExtensions: ConversationExtensions
    private lateinit var messageDAO: MessageDAO
    private lateinit var messageDraftDAO: MessageDraftDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var connectionDAO: ConnectionDAO
    private lateinit var memberDAO: MemberDAO
    private lateinit var userDAO: UserDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        databaseBuilder = createDatabase(selfUserId, encryptedDBSecret, true)
        val db = databaseBuilder
        val queries = db.database.conversationDetailsWithEventsQueries
        messageDAO = db.messageDAO
        messageDraftDAO = db.messageDraftDAO
        conversationDAO = db.conversationDAO
        connectionDAO = db.connectionDAO
        memberDAO = db.memberDAO
        userDAO = db.userDAO
        conversationExtensions = ConversationExtensionsImpl(queries, ConversationDetailsWithEventsMapper, ReadDispatcher(dispatcher))
    }

    @AfterTest
    fun tearDown() {
        deleteDatabase(selfUserId)
    }

    @Test
    fun givenInsertedConversations_whenGettingFirstPage_thenItShouldContainTheCorrectCountBeforeAndAfter() = runTest(dispatcher) {
        populateData(isChannel = false)
        val result = getPager().pagingSource.refresh()
        assertIs<PagingSourceLoadResultPage<Int, ConversationDetailsWithEventsEntity>>(result)
        // Assuming the first page was fetched, itemsAfter should be the remaining ones
        assertEquals(CONVERSATION_COUNT - PAGE_SIZE, result.itemsAfter)
        assertEquals(0, result.itemsBefore) // No items before the first page
    }

    @Test
    fun givenInsertedConversations_whenGettingFirstSearchedPage_thenItShouldContainTheCorrectCountBeforeAndAfter() = runTest(dispatcher) {
        populateData(isChannel = false)
        val searchQuery = "conversation 1"
        val result = getPager(searchQuery = searchQuery).pagingSource.refresh()
        assertIs<PagingSourceLoadResultPage<Int, ConversationDetailsWithEventsEntity>>(result)
        // Assuming the first page was fetched containing only 11 results ("conversation 1" and "conversation 10" to "conversation 19")
        assertEquals(0, result.itemsAfter) // Since the page has fewer elements than PAGE_SIZE, there should be no items after this page
        assertEquals(0, result.itemsBefore) // No items before the first page
    }

    @Test
    fun givenInsertedConversations_whenGettingFirstPage_thenTheNextKeyShouldBeTheFirstItemOfTheNextPage() = runTest(dispatcher) {
        populateData(isChannel = false)
        val result = getPager().pagingSource.refresh()
        assertIs<PagingSourceLoadResultPage<Int, ConversationDetailsWithEventsEntity>>(result)
        assertEquals(PAGE_SIZE, result.nextKey) // First page fetched, second page starts at the end of the first one
    }

    @Test
    fun givenInsertedConversations_whenGettingFirstPage_thenItShouldContainTheFirstPageOfItems() = runTest(dispatcher) {
        populateData(isChannel = false)
        val result = getPager().pagingSource.refresh()
        assertIs<PagingSourceLoadResultPage<Long, ConversationDetailsWithEventsEntity>>(result)
        result.data.forEachIndexed { index, conversation ->
            assertEquals("$CONVERSATION_ID_PREFIX$index", conversation.conversationViewEntity.id.value)
            assertEquals(false, conversation.conversationViewEntity.archived)
        }
    }

    @Test
    fun givenInsertedConversations_whenGettingSecondPage_thenShouldContainTheCorrectItems() = runTest(dispatcher) {
        populateData(isChannel = false)
        val pagingSource = getPager().pagingSource
        val secondPageResult = pagingSource.nextPageForOffset(PAGE_SIZE)
        assertIs<PagingSourceLoadResultPage<Int, ConversationDetailsWithEventsEntity>>(secondPageResult)
        assertFalse { secondPageResult.data.isEmpty() }
        assertTrue { secondPageResult.data.size <= PAGE_SIZE }
        secondPageResult.data.forEachIndexed { index, conversation ->
            assertEquals("$CONVERSATION_ID_PREFIX${index + PAGE_SIZE}", conversation.conversationViewEntity.id.value)
        }
    }

    @Test
    fun givenInsertedConversations_whenGettingFirstPageOfArchivedConversations_thenItShouldContainArchivedItems() = runTest(dispatcher) {
        populateData(archived = false, count = CONVERSATION_COUNT, conversationIdPrefix = CONVERSATION_ID_PREFIX, isChannel = false)
        populateData(archived = true, count = CONVERSATION_COUNT, conversationIdPrefix = ARCHIVED_CONVERSATION_ID_PREFIX, isChannel = false)
        val result = getPager(fromArchive = true).pagingSource.refresh()
        assertIs<PagingSourceLoadResultPage<Long, ConversationDetailsWithEventsEntity>>(result)
        result.data.forEachIndexed { index, conversation ->
            assertEquals("$ARCHIVED_CONVERSATION_ID_PREFIX$index", conversation.conversationViewEntity.id.value)
            assertEquals(true, conversation.conversationViewEntity.archived)
        }
    }

    @Test
    fun givenInsertedConversations_whenGettingFirstSearchedPage_thenShouldContainTheCorrectItems() = runTest(dispatcher) {
        populateData(isChannel = false)
        val searchQuery = "conversation 1"
        val result = getPager(searchQuery = searchQuery).pagingSource.refresh()
        assertIs<PagingSourceLoadResultPage<Int, ConversationDetailsWithEventsEntity>>(result)
        // Assuming the first page was fetched containing only 11 results ["conversation 1" and "conversation 10" to "conversation 19"]
        assertEquals(11, result.data.size)
        result.data.forEachIndexed { index, conversation ->
            assertEquals(true, conversation.conversationViewEntity.name?.contains(searchQuery) ?: false)
        }
    }

    @Test
    fun givenFilterByGroup_whenReturningResults_thenDONotIncludeChannels() = runTest(dispatcher) {
        populateData(archived = false, count = 10, conversationIdPrefix = "group_", isChannel = false)
        populateData(archived = false, count = 9, conversationIdPrefix = "channel_", isChannel = true)
        getPager(searchQuery = "", filter = ConversationFilterEntity.GROUPS).pagingSource.refresh().also { result ->
            assertIs<PagingSourceLoadResultPage<Long, ConversationDetailsWithEventsEntity>>(result)
            assertEquals(10, result.data.size)
            result.data.forEach {
                assertFalse { it.conversationViewEntity.isChannel }
            }
        }
    }

    @Test
    fun givenFilterByChannels_whenReturningResults_thenDONotIncludeGroups() = runTest(dispatcher) {
        populateData(archived = false, count = 10, conversationIdPrefix = "group_", isChannel = false)
        populateData(archived = false, count = 9, conversationIdPrefix = "channel_", isChannel = true)
        getPager(searchQuery = "", filter = ConversationFilterEntity.CHANNELS).pagingSource.refresh().also { result ->
            assertIs<PagingSourceLoadResultPage<Long, ConversationDetailsWithEventsEntity>>(result)
            assertEquals(9, result.data.size)
            result.data.forEach {
                assertTrue { it.conversationViewEntity.isChannel }
            }
        }
    }

    @Test
    fun givenConversationListPagingSource_whenConversationIsInserted_thenItInvalidates() = runTest(dispatcher) {
        populateData(count = 1, isChannel = false)
        val pagingSource = getPager().pagingSource

        pagingSource.refresh()
        val invalidated = pagingSource.observeInvalidation()

        conversationDAO.insertConversation(
            newConversationEntity(ConversationIDEntity("new_conversation", "domain")).copy(
                name = "new conversation",
                type = ConversationEntity.Type.GROUP,
                lastModifiedDate = Instant.parse("2024-01-01T00:00:00Z"),
                lastReadDate = Instant.parse("2023-12-31T23:59:59Z"),
                isChannel = false,
            )
        )

        advanceUntilIdle()

        assertTrue(invalidated())
    }

    @Test
    fun givenConversationListPagingSource_whenMessageIsInserted_thenItInvalidates() = runTest(dispatcher) {
        populateData(count = 1, isChannel = false)
        val conversationId = conversationId()
        val pagingSource = getPager().pagingSource

        pagingSource.refresh()
        val invalidated = pagingSource.observeInvalidation()

        messageDAO.insertOrIgnoreMessage(
            newRegularMessageEntity(
                id = "message_after_load",
                conversationId = conversationId,
                senderUserId = otherUserId,
                date = Instant.parse("2024-01-01T00:00:00Z"),
            )
        )

        advanceUntilIdle()

        assertTrue(invalidated())
    }

    @Test
    fun givenConversationListPagingSource_whenUnreadChanges_thenItInvalidates() = runTest(dispatcher) {
        populateData(count = 1, isChannel = false)
        val conversationId = conversationId()
        messageDAO.insertOrIgnoreMessage(
            newRegularMessageEntity(
                id = "message_before_load",
                conversationId = conversationId,
                senderUserId = otherUserId,
                date = Instant.parse("2024-01-01T00:00:00Z"),
            )
        )
        val pagingSource = getPager().pagingSource

        pagingSource.refresh()
        val invalidated = pagingSource.observeInvalidation()

        databaseBuilder.database.unreadEventsQueries.deleteUnreadEvents(
            Instant.parse("2025-01-01T00:00:00Z"),
            conversationId,
        )

        advanceUntilIdle()

        assertTrue(invalidated())
    }

    @Test
    fun givenConversationListPagingSource_whenDraftChanges_thenItInvalidates() = runTest(dispatcher) {
        populateData(count = 1, isChannel = false)
        val conversationId = conversationId()
        val pagingSource = getPager().pagingSource

        pagingSource.refresh()
        val invalidated = pagingSource.observeInvalidation()

        messageDraftDAO.upsertMessageDraft(newDraftMessageEntity(conversationId = conversationId))

        advanceUntilIdle()

        assertTrue(invalidated())
    }

    @Test
    fun givenConversationListPagingSource_whenUnrelatedMetadataChanges_thenItDoesNotInvalidate() = runTest(dispatcher) {
        populateData(count = 1, isChannel = false)
        val pagingSource = getPager().pagingSource

        pagingSource.refresh()
        val invalidated = pagingSource.observeInvalidation()

        databaseBuilder.database.metadataQueries.insertValue("some_key", "some_value")

        advanceUntilIdle()

        assertFalse(invalidated())
    }

    private fun getPager(searchQuery: String = "", fromArchive: Boolean = false, filter: ConversationFilterEntity = ConversationFilterEntity.ALL): KaliumPager<ConversationDetailsWithEventsEntity> =
        conversationExtensions.getPagerForConversationDetailsWithEventsSearch(
            pagingConfig = PagingConfig(PAGE_SIZE),
            queryConfig = ConversationExtensions.QueryConfig(searchQuery = searchQuery, fromArchive = fromArchive, conversationFilter = filter),
        )

    private suspend fun PagingSource<Int, ConversationDetailsWithEventsEntity>.refresh() =
        load(PagingSourceLoadParamsRefresh<Int>(null, PAGE_SIZE, false))

    private suspend fun PagingSource<Int, ConversationDetailsWithEventsEntity>.nextPageForOffset(key: Int) =
        load(PagingSourceLoadParamsAppend<Int>(key, PAGE_SIZE, true))

    private fun PagingSource<Int, ConversationDetailsWithEventsEntity>.observeInvalidation(): () -> Boolean {
        var invalidated = false
        registerInvalidatedCallback {
            invalidated = true
        }
        return { invalidated }
    }

    private fun conversationId(index: Int = 0, prefix: String = CONVERSATION_ID_PREFIX) = ConversationIDEntity("$prefix$index", "domain")

    private suspend fun populateData(
        archived: Boolean = false,
        count: Int = CONVERSATION_COUNT,
        conversationIdPrefix: String = CONVERSATION_ID_PREFIX,
        isChannel: Boolean
    ) {
        userDAO.upsertUser(newUserEntity(qualifiedID = UserIDEntity("user", "domain")))
        repeat(count) {
            // Ordered by date - Inserting with decreasing date is important to assert pagination
            val lastModified = Instant.fromEpochSeconds(CONVERSATION_COUNT - it.toLong())
            val lastRead = lastModified.minus(1.seconds) // if message needs to be unread, then lastRead should be before lastModified
            val conversation = newConversationEntity(ConversationIDEntity("$conversationIdPrefix$it", "domain")).copy(
                name = "conversation $it",
                type = ConversationEntity.Type.GROUP,
                lastModifiedDate = lastModified,
                lastReadDate = lastRead,
                archived = archived,
                isChannel = isChannel,
            )
            conversationDAO.insertConversation(conversation)
        }
    }

    private companion object {
        const val CONVERSATION_COUNT = 100
        const val CONVERSATION_ID_PREFIX = "conversation_"
        const val ARCHIVED_CONVERSATION_ID_PREFIX = "archived_conversation_"
        const val PAGE_SIZE = 20
        val otherUserId = UserIDEntity("user", "domain")
    }
}
