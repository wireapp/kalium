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
import com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsMapper
import com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationExtensions
import com.wire.kalium.persistence.dao.conversation.ConversationExtensionsImpl
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.persistence.dao.message.KaliumPager
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.draft.MessageDraftDAO
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
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
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        val queries = db.database.conversationsQueries
        messageDAO = db.messageDAO
        messageDraftDAO = db.messageDraftDAO
        conversationDAO = db.conversationDAO
        connectionDAO = db.connectionDAO
        memberDAO = db.memberDAO
        userDAO = db.userDAO
        conversationExtensions = ConversationExtensionsImpl(queries, ConversationDetailsWithEventsMapper, dispatcher)
    }

    @AfterTest
    fun tearDown() {
        deleteDatabase(selfUserId)
    }

    @Test
    fun givenInsertedConversations_whenGettingFirstPage_thenItShouldContainTheCorrectCountBeforeAndAfter() = runTest(dispatcher) {
        populateData()
        val result = getPager().pagingSource.refresh()
        assertIs<PagingSourceLoadResultPage<Int, ConversationDetailsWithEventsEntity>>(result)
        // Assuming the first page was fetched, itemsAfter should be the remaining ones
        assertEquals(CONVERSATION_COUNT - PAGE_SIZE, result.itemsAfter)
        assertEquals(0, result.itemsBefore) // No items before the first page
    }

    @Test
    fun givenInsertedConversations_whenGettingFirstSearchedPage_thenItShouldContainTheCorrectCountBeforeAndAfter() = runTest(dispatcher) {
        populateData()
        val searchQuery = "conversation 1"
        val result = getPager(searchQuery = searchQuery).pagingSource.refresh()
        assertIs<PagingSourceLoadResultPage<Int, ConversationDetailsWithEventsEntity>>(result)
        // Assuming the first page was fetched containing only 11 results ("conversation 1" and "conversation 10" to "conversation 19")
        assertEquals(0, result.itemsAfter) // Since the page has fewer elements than PAGE_SIZE, there should be no items after this page
        assertEquals(0, result.itemsBefore) // No items before the first page
    }

    @Test
    fun givenInsertedConversations_whenGettingFirstPage_thenTheNextKeyShouldBeTheFirstItemOfTheNextPage() = runTest(dispatcher) {
        populateData()
        val result = getPager().pagingSource.refresh()
        assertIs<PagingSourceLoadResultPage<Int, ConversationDetailsWithEventsEntity>>(result)
        assertEquals(PAGE_SIZE, result.nextKey) // First page fetched, second page starts at the end of the first one
    }

    @Test
    fun givenInsertedConversations_whenGettingFirstPage_thenItShouldContainTheFirstPageOfItems() = runTest(dispatcher) {
        populateData()
        val result = getPager().pagingSource.refresh()
        assertIs<PagingSourceLoadResultPage<Long, ConversationDetailsWithEventsEntity>>(result)
        result.data.forEachIndexed { index, conversation ->
            assertEquals("$CONVERSATION_ID_PREFIX$index", conversation.conversationViewEntity.id.value)
            assertEquals(false, conversation.conversationViewEntity.archived)
        }
    }

    @Test
    fun givenInsertedConversations_whenGettingSecondPage_thenShouldContainTheCorrectItems() = runTest(dispatcher) {
        populateData()
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
        populateData(archived = false, count = CONVERSATION_COUNT, conversationIdPrefix = CONVERSATION_ID_PREFIX)
        populateData(archived = true, count = CONVERSATION_COUNT, conversationIdPrefix = ARCHIVED_CONVERSATION_ID_PREFIX)
        val result = getPager(fromArchive = true).pagingSource.refresh()
        assertIs<PagingSourceLoadResultPage<Long, ConversationDetailsWithEventsEntity>>(result)
        result.data.forEachIndexed { index, conversation ->
            assertEquals("$ARCHIVED_CONVERSATION_ID_PREFIX$index", conversation.conversationViewEntity.id.value)
            assertEquals(true, conversation.conversationViewEntity.archived)
        }
    }

    @Test
    fun givenInsertedConversations_whenGettingFirstSearchedPage_thenShouldContainTheCorrectItems() = runTest(dispatcher) {
        populateData()
        val searchQuery = "conversation 1"
        val result = getPager(searchQuery = searchQuery).pagingSource.refresh()
        assertIs<PagingSourceLoadResultPage<Int, ConversationDetailsWithEventsEntity>>(result)
        // Assuming the first page was fetched containing only 11 results ["conversation 1" and "conversation 10" to "conversation 19"]
        assertEquals(11, result.data.size)
        result.data.forEachIndexed { index, conversation ->
            assertEquals(true, conversation.conversationViewEntity.name?.contains(searchQuery) ?: false)
        }
    }

    private fun getPager(searchQuery: String = "", fromArchive: Boolean = false): KaliumPager<ConversationDetailsWithEventsEntity> =
        conversationExtensions.getPagerForConversationDetailsWithEventsSearch(PagingConfig(PAGE_SIZE), searchQuery, fromArchive)

    private suspend fun PagingSource<Int, ConversationDetailsWithEventsEntity>.refresh() =
        load(PagingSourceLoadParamsRefresh<Int>(null, PAGE_SIZE, false))

    private suspend fun PagingSource<Int, ConversationDetailsWithEventsEntity>.nextPageForOffset(key: Int) =
        load(PagingSourceLoadParamsAppend<Int>(key, PAGE_SIZE, true))

    private suspend fun populateData(
        archived: Boolean = false,
        count: Int = CONVERSATION_COUNT,
        conversationIdPrefix: String = CONVERSATION_ID_PREFIX,
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
            )
            conversationDAO.insertConversation(conversation)
        }
    }

    private companion object {
        const val CONVERSATION_COUNT = 100
        const val CONVERSATION_ID_PREFIX = "conversation_"
        const val ARCHIVED_CONVERSATION_ID_PREFIX = "archived_conversation_"
        const val PAGE_SIZE = 20
    }
}
