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
import io.mockative.any
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
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
            coVerify {
                conversationRepository.clearContent(any())
            }.wasInvoked(exactly = once)

            coVerify {
                currentClientIdProvider.invoke()
            }.wasNotInvoked()

            coVerify {
                messageSender.sendMessage(any(), any())
            }.wasNotInvoked()
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
            coVerify {
                conversationRepository.clearContent(any())
            }.wasInvoked(exactly = once)

            coVerify {
                currentClientIdProvider.invoke()
            }.wasInvoked(exactly = once)

            coVerify {
                messageSender.sendMessage(any(), any())
            }.wasNotInvoked()
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
            coVerify {
                conversationRepository.clearContent(any())
            }.wasInvoked(exactly = once)

            coVerify {
                currentClientIdProvider.invoke()
            }.wasInvoked(exactly = once)

            coVerify {
                messageSender.sendMessage(any(), any())
            }.wasInvoked(exactly = once)
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
            coVerify {
                conversationRepository.clearContent(any())
            }.wasInvoked(exactly = once)

            coVerify {
                currentClientIdProvider.invoke()
            }.wasInvoked(exactly = once)

            coVerify {
                messageSender.sendMessage(any(), any())
            }.wasInvoked(exactly = once)
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

        suspend fun withClearConversationContent(isSuccessFull: Boolean) = apply {
            coEvery {
                conversationRepository.clearContent(any())
            }.returns(if (isSuccessFull) Either.Right(Unit) else Either.Left(CoreFailure.Unknown(Throwable("an error"))))
        }

        suspend fun withCurrentClientId(isSuccessFull: Boolean): Arrangement {
            coEvery { currentClientIdProvider() }
                .returns(
                    if (isSuccessFull) Either.Right(TestClient.CLIENT_ID)
                    else Either.Left(CoreFailure.Unknown(Throwable("an error")))
                )
            return this
        }

        suspend fun withMessageSending(isSuccessFull: Boolean): Arrangement {
            coEvery {
                messageSender.sendMessage(any(), any())
            }.returns(if (isSuccessFull) Either.Right(Unit) else Either.Left(CoreFailure.Unknown(Throwable("an error"))))

            return this
        }

        suspend fun withSelfConversationIds(conversationIds: List<ConversationId>) = apply {
            coEvery {
                selfConversationIdProvider.invoke()
            }.returns(Either.Right(conversationIds))
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
