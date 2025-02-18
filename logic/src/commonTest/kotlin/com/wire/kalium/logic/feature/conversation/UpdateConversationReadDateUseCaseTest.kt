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

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageTarget
import com.wire.kalium.logic.feature.message.receipt.ConversationWorkQueue
import com.wire.kalium.logic.feature.message.receipt.InstantConversationWorkQueue
import com.wire.kalium.logic.feature.message.receipt.SendConfirmationUseCase
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.arrangement.MessageSenderArrangement
import com.wire.kalium.logic.util.arrangement.MessageSenderArrangementImpl
import com.wire.kalium.logic.util.arrangement.SelfConversationIdProviderArrangement
import com.wire.kalium.logic.util.arrangement.SelfConversationIdProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class UpdateConversationReadDateUseCaseTest {

    @Test
    fun givenCurrentStoredLastReadDateIsNewerThanEnqueued_whenWorking_thenShouldNotTryToDoAnyWork() = runTest {
        val persistedLastRead = Clock.System.now()
        val conversationId = TestConversation.CONVERSATION.id
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withObserveByIdReturning(
                TestConversation.CONVERSATION.copy(lastReadDate = persistedLastRead)
            )
        }

        updateConversationReadDateUseCase(conversationId, persistedLastRead - 1.seconds)

        coVerify {
            arrangement.sendConfirmation(any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.conversationRepository.updateConversationReadDate(any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.messageSender.sendMessage(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenProvidedTimeIsNewerThanPersistedLastReadForConversation_whenWorking_thenShouldTryToSendReceipts() = runTest {
        val persistedLastRead = Clock.System.now()
        val newLastRead = persistedLastRead + 1.seconds
        val conversationId = TestConversation.CONVERSATION.id
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withObserveByIdReturning(
                TestConversation.CONVERSATION.copy(lastReadDate = persistedLastRead)
            )
        }

        updateConversationReadDateUseCase(conversationId, newLastRead)

        coVerify {
            arrangement.sendConfirmation(eq(conversationId), eq(persistedLastRead), eq(newLastRead))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenProvidedTimeIsNewerThanPersistedLastReadForConversation_whenWorking_thenShouldUpdateLastReadLocally() = runTest {
        val persistedLastRead = Clock.System.now()
        val newLastRead = persistedLastRead + 1.seconds
        val conversationId = TestConversation.CONVERSATION.id
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withObserveByIdReturning(
                TestConversation.CONVERSATION.copy(lastReadDate = persistedLastRead)
            )
        }

        updateConversationReadDateUseCase(conversationId, newLastRead)

        coVerify {
            arrangement.conversationRepository.updateConversationReadDate(eq(conversationId), eq(newLastRead))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenProvidedTimeIsNewerThanPersistedLastReadForConversation_whenWorking_thenShouldUpdateLastReadForOtherSelfClients() = runTest {
        val persistedLastRead = Clock.System.now()
        val newLastRead = persistedLastRead + 1.seconds
        val conversationId = TestConversation.CONVERSATION.id
        val (arrangement, updateConversationReadDateUseCase) = arrange {
            withObserveByIdReturning(
                TestConversation.CONVERSATION.copy(lastReadDate = persistedLastRead)
            )
        }

        updateConversationReadDateUseCase(conversationId, newLastRead)

        coVerify {
            arrangement.messageSender.sendMessage(
                message = matches { message ->
                    val content = message.content
                    assertIs<MessageContent.LastRead>(content)
                    assertEquals(conversationId, content.conversationId)
                    assertEquals(newLastRead, content.time)
                    assertEquals(arrangement.selfConversationId, message.conversationId)
                    true
                },
                messageTarget = matches { target ->
                    assertIs<MessageTarget.Conversation>(target)
                    true
                }
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAnyCall_whenInvoking_thenShouldEnqueueWork() = runTest {
        val expectedId = TestConversation.CONVERSATION.id.copy(value = "potato")
        val expectedTime = Clock.System.now()
        lateinit var enqueuedId: ConversationId
        lateinit var enqueuedInstant: Instant
        var enqueuedTimes = 0
        val (_, updateConversationReadDateUseCase) = arrange {
            workQueue = ConversationWorkQueue { input, _ ->
                enqueuedTimes += 1
                enqueuedInstant = input.eventTime
                enqueuedId = input.conversationId
            }
        }
        updateConversationReadDateUseCase(expectedId, expectedTime)

        assertEquals(1, enqueuedTimes)
        assertEquals(expectedId, enqueuedId)
        assertEquals(expectedTime, enqueuedInstant)
    }

    private class Arrangement(
        private val configure: suspend Arrangement.() -> Unit
    ) : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        MessageSenderArrangement by MessageSenderArrangementImpl(),
        SelfConversationIdProviderArrangement by SelfConversationIdProviderArrangementImpl() {

        var currentClientId = TestClient.CLIENT_ID
        var selfUserID = TestUser.SELF.id
        var selfConversationId = TestConversation.SELF().id.copy("SELF")

        @Mock
        val sendConfirmation = mock(SendConfirmationUseCase::class)

        var workQueue: ConversationWorkQueue = InstantConversationWorkQueue()

        suspend fun arrange(): Pair<Arrangement, UpdateConversationReadDateUseCase> = run {
            coEvery {
                sendConfirmation(any(), any(), any())
            }.returns(Either.Right(Unit))
            coEvery {
                conversationRepository.updateConversationReadDate(any(), any())
            }.returns(Either.Right(Unit))
            coEvery {
                messageSender.sendMessage(any(), any())
            }.returns(Either.Right(Unit))
            withSelfConversationIds(listOf(selfConversationId))
            configure()
            this@Arrangement to UpdateConversationReadDateUseCase(
                conversationRepository,
                messageSender,
                { Either.Right(currentClientId) },
                selfUserID,
                selfConversationIdProvider,
                sendConfirmation,
                workQueue
            )
        }
    }

    private companion object {
        suspend fun arrange(configure: suspend Arrangement.() -> Unit) = Arrangement(configure).arrange()
    }
}
