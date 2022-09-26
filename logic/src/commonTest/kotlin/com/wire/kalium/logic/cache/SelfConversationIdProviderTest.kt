package com.wire.kalium.logic.cache

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SelfConversationIdProviderTest {

    @Test
    fun givenFirstTimeSelfConversationIdProviderIsCalled_thenValueIsFetchedFromDB() = runTest {
        val expected = Either.Right(SELF_CONVERSATION_ID)
        val (arrangment, selfConversationIdProvider) = Arrangement()
            .withSelfConversationId(expected)
            .arrange()

        selfConversationIdProvider().also {
            assertIs<Either.Right<ConversationId>>(it)
            assertEquals(expected.value, it.value)
        }

        verify(arrangment.conversationRepository)
            .suspendFunction(arrangment.conversationRepository::getSelfConversationId)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenFirstTimeSelfConversationIdProviderIsCalled_whenGetSelfConversationIdFail_thenErrorIsProbated() = runTest {
        val expected = Either.Left(StorageFailure.DataNotFound)
        val (arrangement, selfConversationIdProvider) = Arrangement()
            .withSelfConversationId(expected)
            .arrange()

        selfConversationIdProvider().also {
            assertIs<Either.Left<StorageFailure>>(it)
            assertEquals(expected.value, it.value)
        }

        selfConversationIdProvider().also {
            assertIs<Either.Left<StorageFailure>>(it)
            assertEquals(expected.value, it.value)
        }

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::getSelfConversationId)
            .wasInvoked(exactly = twice)
    }

    @Test
    fun whenInvokingMultipleTimes_thenOnlyTheFirstInvokeWillFetchDataFromDB() = runTest {
        val expected = Either.Right(SELF_CONVERSATION_ID)
        val (arrangment, selfConversationIdProvider) = Arrangement()
            .withSelfConversationId(expected)
            .arrange()

        for (i in 0..5) {
            selfConversationIdProvider().also {
                assertIs<Either.Right<ConversationId>>(it)
                assertEquals(expected.value, it.value)
            }
        }
        verify(arrangment.conversationRepository)
            .suspendFunction(arrangment.conversationRepository::getSelfConversationId)
            .wasInvoked(exactly = once)
    }


    private companion object {
        val SELF_CONVERSATION_ID = ConversationId("self_conv_id", "conv_domain")
    }


    private class Arrangement {

        @Mock
        val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

        val selfConversationIdProvider: SelfConversationIdProvider = SelfConversationIdProviderImpl(conversationRepository)

        suspend fun withSelfConversationId(result: Either<StorageFailure, ConversationId>): Arrangement = apply {
            given(conversationRepository).coroutine { getSelfConversationId() }.then { result }
        }

        fun arrange() = this to selfConversationIdProvider
    }
}
