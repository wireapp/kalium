/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.persistence.dao.message

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MessageExtensionsTest : BaseDatabaseTest() {

    private lateinit var messageExtensions: MessageExtensions
    private lateinit var messageDAO: MessageDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)

        val messagesQueries = db.database.messagesQueries
        messageDAO = db.messageDAO
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
        messageExtensions = MessageExtensionsImpl(messagesQueries, MessageMapper, dispatcher)
    }

    @After
    fun tearDown() {
        deleteDatabase(selfUserId)
    }

    @Test
    fun givenInsertedMessages_whenGettingFirstPage_thenItShouldContainTheCorrectCountBeforeAndAfter() = runTest {
        populateMessageData()

        val result = getPager().pagingSource.refresh()

        assertIs<PagingSource.LoadResult.Page<Int, MessageEntity>>(result)
        // Assuming the first page was fetched, itemsAfter should be the remaining ones
        assertEquals(MESSAGE_COUNT - PAGE_SIZE, result.itemsAfter)
        // No items before the first page
        assertEquals(0, result.itemsBefore)
    }

    @Test
    fun givenInsertedMessages_whenGettingFirstPage_thenItShouldContainTheFirstPageOfItems() = runTest {
        populateMessageData()

        val result = getPager().pagingSource.refresh()

        assertIs<PagingSource.LoadResult.Page<Long, MessageEntity>>(result)

        result.data.forEachIndexed { index, message ->
            assertEquals(index.toString(), message.id)
        }
    }

    @Test
    fun givenInsertedMessages_whenGettingFirstPage_thenTheNextKeyShouldBeTheFirstItemOfTheNextPage() = runTest {
        populateMessageData()

        val result = getPager().pagingSource.refresh()

        assertIs<PagingSource.LoadResult.Page<Int, MessageEntity>>(result)
        // First page fetched, second page starts at the end of the first one
        assertEquals(PAGE_SIZE, result.nextKey)
    }

    @Test
    fun givenInsertedMessages_whenGettingSecondPage_thenShouldContainTheCorrectItems() = runTest {
        populateMessageData()

        val pagingSource = getPager().pagingSource
        val secondPageResult = pagingSource.nextPageForOffset(PAGE_SIZE)

        assertIs<PagingSource.LoadResult.Page<Int, MessageEntity>>(secondPageResult)
        assertFalse { secondPageResult.data.isEmpty() }
        assertTrue { secondPageResult.data.size <= PAGE_SIZE }
        secondPageResult.data.forEachIndexed { index, message ->
            assertEquals((index + PAGE_SIZE).toString(), message.id)
        }
    }

    private fun getPager(): KaliumPager<MessageEntity> = messageExtensions.getPagerForConversation(
        conversationId = CONVERSATION_ID,
        visibilities = MessageEntity.Visibility.values().toList(),
        pagingConfig = PagingConfig(PAGE_SIZE),
        startingOffset = 0
    )

    private suspend fun PagingSource<Int, MessageEntity>.refresh() = load(
        PagingSource.LoadParams.Refresh(null, PAGE_SIZE, true)
    )

    private suspend fun PagingSource<Int, MessageEntity>.nextPageForOffset(key: Int) = load(
        PagingSource.LoadParams.Append(key, PAGE_SIZE, true)
    )

    private suspend fun populateMessageData() {
        val userId = UserIDEntity("user", "domain")
        userDAO.upsertUser(newUserEntity(qualifiedID = userId))
        conversationDAO.insertConversation(newConversationEntity(id = CONVERSATION_ID))
        val messages = buildList<MessageEntity> {
            repeat(MESSAGE_COUNT) {
                add(
                    newRegularMessageEntity(
                        id = it.toString(),
                        conversationId = CONVERSATION_ID,
                        senderUserId = userId,
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
