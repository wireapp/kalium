package com.wire.kalium.persistence.dao.message

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MessageExtensionsTest : BaseDatabaseTest() {

    private lateinit var messageExtensions: MessageExtensions
    private lateinit var messageDAO: MessageDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO

    @Before
    fun setUp() {
        deleteDatabase()
        val db = createDatabase()

        val messagesQueries = db.database.messagesQueries
        messageDAO = db.messageDAO
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
        messageExtensions = MessageExtensionsImpl(messagesQueries, MessageMapper(messagesQueries))
    }

    @After
    fun tearDown() {
        deleteDatabase()
    }

    @Test
    fun givenInsertedMessages_whenGettingFirstPage_thenItShouldContainTheCorrectCountBeforeAndAfter() = runTest {
        populateMessageData()

        val result = getPager().pagingSource.refresh()

        assertIs<PagingSource.LoadResult.Page<Long, MessageEntity>>(result)
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

        assertIs<PagingSource.LoadResult.Page<Long, MessageEntity>>(result)
        // First page fetched, second page starts at the end of the first one
        assertEquals(PAGE_SIZE.toLong(), result.nextKey)
    }

    @Test
    fun givenInsertedMessages_whenGettingSecondPage_thenShouldContainTheCorrectItems() = runTest {
        populateMessageData()

        val pagingSource = getPager().pagingSource
        val secondPageResult = pagingSource.nextPageForOffset(PAGE_SIZE.toLong())

        assertIs<PagingSource.LoadResult.Page<Long, MessageEntity>>(secondPageResult)

        secondPageResult.data.forEachIndexed { index, message ->
            assertEquals((index + PAGE_SIZE).toString(), message.id)
        }
    }

    private suspend fun getPager(): KaliumPager<MessageEntity> = messageExtensions.getPagerForConversation(
            conversationId = CONVERSATION_ID,
            visibilities = MessageEntity.Visibility.values().toList(),
            pagingConfig = PagingConfig(PAGE_SIZE)
        )

    private suspend fun PagingSource<Long, MessageEntity>.refresh() = load(
        PagingSource.LoadParams.Refresh(null, PAGE_SIZE, true)
    )

    private suspend fun PagingSource<Long, MessageEntity>.nextPageForOffset(key: Long) = load(
        PagingSource.LoadParams.Append(key, PAGE_SIZE, true)
    )

    private suspend fun populateMessageData() {
        val userId = UserIDEntity("user", "domain")
        userDAO.insertUser(newUserEntity(qualifiedID = userId))
        conversationDAO.insertConversation(newConversationEntity(id = CONVERSATION_ID))
        val messages = buildList<MessageEntity> {
            repeat(MESSAGE_COUNT) {
                add(
                    newRegularMessageEntity(
                        id = it.toString(),
                        conversationId = CONVERSATION_ID,
                        senderUserId = userId
                    )
                )
            }
        }
        messageDAO.insertMessages(messages)
    }

    private companion object {
        const val MESSAGE_COUNT = 100
        const val PAGE_SIZE = 20
        val CONVERSATION_ID = ConversationIDEntity("conversation", "domain")
    }
}
