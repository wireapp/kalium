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
package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.UpdateConversationProtocolUseCase
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.sync.receiver.conversation.ProtocolUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ProtocolUpdateEventHandlerImpl
import com.wire.kalium.logic.util.arrangement.SystemMessageInserterArrangement
import com.wire.kalium.logic.util.arrangement.SystemMessageInserterArrangementImpl
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.CallRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.CallRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtocolUpdateEventHandlerTest {

    @Test
    fun givenEventIsSuccessfullyConsumed_whenHandlerInvoked_thenProtocolIsUpdatedLocally() = runTest {
        val event = TestEvent.newConversationProtocolEvent()

        val (arrangement, useCase) = arrange {
            withUpdateProtocolUpdateReturns(Either.Right(true))
            withInsertProtocolChangedSystemMessage()
            withoutAnyEstablishedCall()
        }

        useCase.handle(arrangement.transactionContext, event).shouldSucceed()

        coVerify {
            arrangement.updateConversationProtocol(any(), eq(event.conversationId), eq(event.protocol), eq(true))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.systemMessageInserter.insertProtocolChangedSystemMessage(any(), any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenProtocolUpdatedDuringACall_whenHandlingEvent_ThenInsertSystemMessages() = runTest {
        val event = TestEvent.newConversationProtocolEvent()

        val (arrangement, useCase) = arrange {
            withUpdateProtocolUpdateReturns(Either.Right(true))
            withInsertProtocolChangedSystemMessage()
            withEstablishedCall()
        }

        useCase.handle(arrangement.transactionContext, event).shouldSucceed()

        coVerify {
            arrangement.updateConversationProtocol(any(), eq(event.conversationId), eq(event.protocol), eq(true))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.systemMessageInserter.insertProtocolChangedSystemMessage(any(), any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.systemMessageInserter.insertProtocolChangedDuringACallSystemMessage(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenEventFailsToBeConsumed_whenHandlerInvoked_thenErrorIsPropagated() = runTest {
        val event = TestEvent.newConversationProtocolEvent()
        val failure = NetworkFailure.NoNetworkConnection(null)

        val (arrangement, useCase) = arrange {
            withUpdateProtocolUpdateReturns(Either.Left(failure))
            withInsertProtocolChangedSystemMessage()
        }

        useCase.handle(arrangement.transactionContext, event).shouldFail {
            assertEquals(failure, it)
        }

        coVerify {
            arrangement.updateConversationProtocol(any(), eq(event.conversationId), eq(event.protocol), eq(true))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenProtocolWasNotAlreadyUpdated_whenHandlerInvoked_thenSystemMessageIsInserted() = runTest {
        val event = TestEvent.newConversationProtocolEvent()

        val (arrangement, useCase) = arrange {
            withUpdateProtocolUpdateReturns(Either.Right(true))
            withInsertProtocolChangedSystemMessage()
            withoutAnyEstablishedCall()
        }

        useCase.handle(arrangement.transactionContext, event).shouldSucceed()

        coVerify {
            arrangement.updateConversationProtocol(any(), eq(event.conversationId), eq(event.protocol), eq(true))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenProtocolWasAlreadyUpdated_whenHandlerInvoked_thenSystemMessageIsNotInserted() = runTest {
        val event = TestEvent.newConversationProtocolEvent()

        val (arrangement, useCase) = arrange {
            withUpdateProtocolUpdateReturns(Either.Right(false))
            withInsertProtocolChangedSystemMessage()
        }

        useCase.handle(arrangement.transactionContext, event).shouldSucceed()

        coVerify {
            arrangement.systemMessageInserter.insertProtocolChangedSystemMessage(
                eq(event.conversationId),
                eq(event.senderUserId),
                eq(event.protocol)
            )
        }.wasNotInvoked()
    }

    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        SystemMessageInserterArrangement by SystemMessageInserterArrangementImpl(),
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl(),
        CallRepositoryArrangement by CallRepositoryArrangementImpl() {
        val updateConversationProtocol = mock(UpdateConversationProtocolUseCase::class)

        private val protocolUpdateEventHandler: ProtocolUpdateEventHandler = ProtocolUpdateEventHandlerImpl(
            systemMessageInserter,
            callRepository,
            updateConversationProtocol
        )

        suspend fun withUpdateProtocolUpdateReturns(result: Either<CoreFailure, Boolean>) {
            coEvery {
                updateConversationProtocol(any(), any(), any(), any())
            }.returns(result)
        }

        fun arrange() = run {
            runBlocking { block() }
            this@Arrangement to protocolUpdateEventHandler
        }
    }

    companion object {
        private fun arrange(configuration: suspend Arrangement.() -> Unit) = Arrangement(configuration).arrange()
    }
}
