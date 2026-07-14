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
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.UpdateConversationProtocolUseCase
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.sync.receiver.conversation.ProtocolUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ProtocolUpdateEventHandlerImpl
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.flowOf
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateConversationProtocol(any(), eq(event.conversationId), eq(event.protocol), eq(true))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.systemMessageInserter.insertProtocolChangedSystemMessage(any(), any(), any())
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateConversationProtocol(any(), eq(event.conversationId), eq(event.protocol), eq(true))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.systemMessageInserter.insertProtocolChangedSystemMessage(any(), any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.systemMessageInserter.insertProtocolChangedDuringACallSystemMessage(any(), any())
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateConversationProtocol(any(), eq(event.conversationId), eq(event.protocol), eq(true))
        }
    }

    @Test
    fun givenStaleProtocolEventForDeletedConversation_whenBackendReturnsNoConversation_thenEventIsSkipped() = runTest {
        val event = TestEvent.newConversationProtocolEvent()
        val failure = NetworkFailure.ServerMiscommunication(TestNetworkException.noConversation)

        val (arrangement, useCase) = arrange {
            withUpdateProtocolUpdateReturns(Either.Left(failure))
        }

        useCase.handle(arrangement.transactionContext, event).shouldSucceed()

        verifySuspend(VerifyMode.not) {
            arrangement.systemMessageInserter.insertProtocolChangedSystemMessage(any(), any(), any())
        }
    }

    @Test
    fun givenProtocolEvent_whenBackendReturnsOtherServerError_thenErrorIsPropagated() = runTest {
        val event = TestEvent.newConversationProtocolEvent()
        val failure = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)

        val (arrangement, useCase) = arrange {
            withUpdateProtocolUpdateReturns(Either.Left(failure))
        }

        useCase.handle(arrangement.transactionContext, event).shouldFail {
            assertEquals(failure, it)
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateConversationProtocol(any(), eq(event.conversationId), eq(event.protocol), eq(true))
        }
    }

    @Test
    fun givenProtocolWasAlreadyUpdated_whenHandlerInvoked_thenSystemMessageIsNotInserted() = runTest {
        val event = TestEvent.newConversationProtocolEvent()

        val (arrangement, useCase) = arrange {
            withUpdateProtocolUpdateReturns(Either.Right(false))
            withInsertProtocolChangedSystemMessage()
        }

        useCase.handle(arrangement.transactionContext, event).shouldSucceed()

        verifySuspend(VerifyMode.not) {
            arrangement.systemMessageInserter.insertProtocolChangedSystemMessage(
                eq(event.conversationId),
                eq(event.senderUserId),
                eq(event.protocol)
            )
        }
    }

    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val updateConversationProtocol = mock<UpdateConversationProtocolUseCase>()
        val systemMessageInserter = mock<SystemMessageInserter>(mode = MockMode.autoUnit)
        val callRepository = mock<CallRepository>()

        private val protocolUpdateEventHandler: ProtocolUpdateEventHandler = ProtocolUpdateEventHandlerImpl(
            systemMessageInserter,
            callRepository,
            updateConversationProtocol
        )

        suspend fun withUpdateProtocolUpdateReturns(result: Either<CoreFailure, Boolean>) {
            everySuspend {
                updateConversationProtocol(any(), any(), any(), any())
            } returns result
        }

        suspend fun withInsertProtocolChangedSystemMessage() {
            everySuspend {
                systemMessageInserter.insertProtocolChangedSystemMessage(any(), any(), any())
            } returns Unit
        }

        suspend fun withEstablishedCall() {
            everySuspend {
                callRepository.establishedCallsFlow()
            } returns flowOf(listOf(com.wire.kalium.logic.util.arrangement.repository.CallRepositoryArrangementImpl.call))
        }

        suspend fun withoutAnyEstablishedCall() {
            everySuspend {
                callRepository.establishedCallsFlow()
            } returns flowOf(emptyList())
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
