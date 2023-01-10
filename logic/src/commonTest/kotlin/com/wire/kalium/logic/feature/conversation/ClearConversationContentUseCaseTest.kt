package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.Times
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class ClearConversationContentUseCaseTest {

    @Test
    fun givenClearConversationFails_whenInvoking_thenCorrectlyPropagateFailure() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withClearConversationContent(false)
            .withMessageSending(true)
            .withCurrentClientId((true))
            .withSelfConversationIds(listOf(selfConversationId))
            .arrange()

        // when
        val result = useCase(ConversationId("someValue", "someDomain"))

        // then
        assertIs<ClearConversationContentUseCase.Result.Failure>(result)

        with(arrangement) {
            verify(clearConversationContent)
                .suspendFunction(clearConversationContent::invoke)
                .with(anything())
                .wasInvoked(Times(1))

            verify(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .wasNotInvoked()

            verify(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .with(anything(), anything())
                .wasNotInvoked()
        }
    }

    @Test
    fun givenGettingClientIdFails_whenInvoking_thenCorrectlyPropagateFailure() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withClearConversationContent(true)
            .withCurrentClientId(false)
            .withMessageSending(true)
            .withSelfConversationIds(listOf(selfConversationId))
            .arrange()

        // when
        val result = useCase(ConversationId("someValue", "someDomain"))

        // then
        assertIs<ClearConversationContentUseCase.Result.Failure>(result)

        with(arrangement) {
            verify(clearConversationContent)
                .suspendFunction(clearConversationContent::invoke)
                .with(anything())
                .wasInvoked(Times(1))

            verify(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .wasInvoked(Times(1))

            verify(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .with(anything(), anything())
                .wasNotInvoked()
        }
    }

    @Test
    fun givenSendMessageFails_whenInvoking_thenCorrectlyPropagateFailure() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withClearConversationContent(true)
            .withCurrentClientId(true)
            .withMessageSending(false)
            .withSelfConversationIds(listOf(selfConversationId))
            .arrange()

        // when
        val result = useCase(ConversationId("someValue", "someDomain"))

        // then
        assertIs<ClearConversationContentUseCase.Result.Failure>(result)

        with(arrangement) {
            verify(clearConversationContent)
                .suspendFunction(clearConversationContent::invoke)
                .with(anything())
                .wasInvoked(Times(1))

            verify(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .wasInvoked(Times(1))

            verify(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .with(anything(), anything())
                .wasInvoked(Times(1))
        }
    }

    @Test
    fun givenClearingConversationSucceeds_whenInvoking_thenCorrectlyPropagateSuccess() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withClearConversationContent(true)
            .withCurrentClientId(true)
            .withMessageSending(true)
            .withSelfConversationIds(listOf(selfConversationId))
            .arrange()

        // when
        val result = useCase(ConversationId("someValue", "someDomain"))

        // then
        assertIs<ClearConversationContentUseCase.Result.Success>(result)

        with(arrangement) {
            verify(clearConversationContent)
                .suspendFunction(clearConversationContent::invoke)
                .with(anything())
                .wasInvoked(Times(1))

            verify(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .wasInvoked(Times(1))

            verify(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .with(anything(), anything())
                .wasInvoked(Times(1))
        }
    }

    private companion object {
        val selfConversationId = ConversationId("self_conversation_id", "self_domain")
    }

    private class Arrangement {

        @Mock
        val clearConversationContent = mock(classOf<ClearConversationContent>())

        @Mock
        val currentClientIdProvider = mock(classOf<CurrentClientIdProvider>())

        @Mock
        val selfConversationIdProvider: SelfConversationIdProvider = mock(SelfConversationIdProvider::class)

        @Mock
        val messageSender = mock(classOf<MessageSender>())

        fun withClearConversationContent(isSuccessFull: Boolean): Arrangement {
            given(clearConversationContent)
                .suspendFunction(clearConversationContent::invoke)
                .whenInvokedWith(anything())
                .thenReturn(if (isSuccessFull) Either.Right(Unit) else Either.Left(CoreFailure.Unknown(Throwable("an error"))))

            return this
        }

        fun withCurrentClientId(isSuccessFull: Boolean): Arrangement {
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(
                    if (isSuccessFull) Either.Right(TestClient.CLIENT_ID)
                    else Either.Left(CoreFailure.Unknown(Throwable("an error")))
                )

            return this
        }

        fun withMessageSending(isSuccessFull: Boolean): Arrangement {
            given(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .whenInvokedWith(anything(), anything())
                .thenReturn(if (isSuccessFull) Either.Right(Unit) else Either.Left(CoreFailure.Unknown(Throwable("an error"))))

            return this
        }

        suspend fun withSelfConversationIds(conversationIds: List<ConversationId>) = apply {
            given(selfConversationIdProvider).coroutine { invoke() }.then { Either.Right(conversationIds) }
        }

        fun arrange() = this to ClearConversationContentUseCaseImpl(
            clearConversationContent,
            messageSender,
            TestUser.SELF.id,
            currentClientIdProvider,
            selfConversationIdProvider
        )
    }

}
