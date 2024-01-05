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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
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
            verify(conversationRepository)
                .suspendFunction(conversationRepository::clearContent)
                .with(anything())
                .wasInvoked(exactly = once)

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
            verify(conversationRepository)
                .suspendFunction(conversationRepository::clearContent)
                .with(anything())
                .wasInvoked(exactly = once)

            verify(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .wasInvoked(exactly = once)

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
            verify(conversationRepository)
                .suspendFunction(conversationRepository::clearContent)
                .with(anything())
                .wasInvoked(exactly = once)

            verify(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .wasInvoked(exactly = once)

            verify(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .with(anything(), anything())
                .wasInvoked(exactly = once)
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
            verify(conversationRepository)
                .suspendFunction(conversationRepository::clearContent)
                .with(anything())
                .wasInvoked(exactly = once)

            verify(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .wasInvoked(exactly = once)

            verify(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .with(anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    private companion object {
        val selfConversationId = ConversationId("self_conversation_id", "self_domain")
    }

    private class Arrangement {

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())
        @Mock
        val currentClientIdProvider = mock(classOf<CurrentClientIdProvider>())

        @Mock
        val selfConversationIdProvider: SelfConversationIdProvider = mock(SelfConversationIdProvider::class)

        @Mock
        val messageSender = mock(classOf<MessageSender>())

        fun withClearConversationContent(isSuccessFull: Boolean) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::clearContent)
                .whenInvokedWith(anything())
                .thenReturn(if (isSuccessFull) Either.Right(Unit) else Either.Left(CoreFailure.Unknown(Throwable("an error"))))
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
            conversationRepository,
            messageSender,
            TestUser.SELF.id,
            currentClientIdProvider,
            selfConversationIdProvider
        )
    }

}
