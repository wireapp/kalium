package com.wire.kalium.logic.data.message

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.persistence.dao.message.KaliumPager
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageExtensions
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import com.wire.kalium.persistence.Message as PersistenceMessage

class MessageRepositoryExtensionsTest {

    private val fakePagingSource = object : PagingSource<Long, PersistenceMessage>() {
        override fun getRefreshKey(state: PagingState<Long, PersistenceMessage>): Long? = null

        override suspend fun load(params: LoadParams<Long>): LoadResult<Long, PersistenceMessage> =
            LoadResult.Error(NotImplementedError("STUB for tests. Not implemented."))
    }

    @Test
    fun givenParameters_whenPaginatedMessagesByConversation_thenShouldCallDaoExtensionsWithRightParameters() = runTest {
        val pagingConfig = PagingConfig(20)
        val pager = Pager(pagingConfig) { fakePagingSource }

        val kaliumPager = KaliumPager<PersistenceMessage, MessageEntity>(pager, fakePagingSource) { MESSAGE_ENTITY }
        val (arrangement, messageRepositoryExtensions) = Arrangement()
            .withMessageExtensionsReturningPager(kaliumPager)
            .arrange()

        val visibilities = listOf(Message.Visibility.VISIBLE)
        val result: Flow<PagingData<Message>> = messageRepositoryExtensions.getPaginatedMessagesByConversationIdAndVisibility(
            TestConversation.ID,
            visibilities,
            pagingConfig
        )

        verify(arrangement.messageDaoExtensions)
            .function(arrangement.messageDaoExtensions::getPagerForConversation)
            .with(eq(CONVERSATION_ID_ENTITY), matching {
                val list = it.toList()
                list.size == 1 && list[0] == MessageEntity.Visibility.VISIBLE
            }, eq(pagingConfig))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val messageDaoExtensions: MessageExtensions = mock(MessageExtensions::class)

        @Mock
        private val messageDAO: MessageDAO = mock(MessageDAO::class)

        @Mock
        private val idMapper: IdMapper = mock(IdMapper::class)

        @Mock
        private val messageMapper: MessageMapper = mock(MessageMapper::class)

        init {
            given(idMapper)
                .function(idMapper::toDaoModel)
                .whenInvokedWith(any())
                .thenReturn(CONVERSATION_ID_ENTITY)

            given(messageMapper)
                .function(messageMapper::fromEntityToMessage)
                .whenInvokedWith(any())
                .thenReturn(MESSAGE)

            given(messageDAO)
                .getter(messageDAO::platformExtensions)
                .whenInvoked()
                .thenReturn(messageDaoExtensions)
        }

        fun withMessageExtensionsReturningPager(kaliumPager: KaliumPager<PersistenceMessage, MessageEntity>) = apply {
            given(messageDaoExtensions)
                .function(messageDaoExtensions::getPagerForConversation)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(kaliumPager)
        }

        private val messageRepositoryExtensions: MessageRepositoryExtensions by lazy {
            MessageRepositoryExtensionsImpl(
                messageDAO,
                idMapper,
                messageMapper
            )
        }

        fun arrange() = this to messageRepositoryExtensions

    }

    private companion object {
        val CONVERSATION_ID_ENTITY = TestConversation.ENTITY_ID
        val MESSAGE = TestMessage.TEXT_MESSAGE
        val MESSAGE_ENTITY = TestMessage.ENTITY
    }
}
