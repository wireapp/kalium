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
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.receiver.conversation.ProtocolUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ProtocolUpdateEventHandlerImpl
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class ProtocolUpdateEventHandlerTest {

    @Test
    fun givenEventIsSuccessfullyConsumed_whenHandlerInvoked_thenProtocolIsUpdatedLocally() = runTest {
        val event = conversationProtocolEvent()
        val (arrangement, handler) = arrange {
            withUpdateProtocolReturning(Either.Right(true))
            withoutAnyEstablishedCall()
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, event))

        val call = arrangement.updateConversationProtocol.calls.single()
        assertSame(arrangement.transactionContext, call.transactionContext)
        assertEquals(event.conversationId, call.conversationId)
        assertEquals(event.protocol, call.protocol)
        assertTrue(call.localOnly)
        assertEquals(
            listOf(ProtocolMessageCall(event.conversationId, event.senderUserId, event.protocol)),
            arrangement.protocolMessages,
        )
    }

    @Test
    fun givenProtocolUpdatedDuringACall_whenHandlingEvent_ThenInsertSystemMessages() = runTest {
        val event = conversationProtocolEvent()
        val (arrangement, handler) = arrange {
            withUpdateProtocolReturning(Either.Right(true))
            withEstablishedCall()
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, event))

        assertEquals(
            UpdateCall(arrangement.transactionContext, event.conversationId, event.protocol, localOnly = true),
            arrangement.updateConversationProtocol.calls.single(),
        )
        assertEquals(
            listOf(ProtocolMessageCall(event.conversationId, event.senderUserId, event.protocol)),
            arrangement.protocolMessages,
        )
        assertEquals(
            listOf(DuringCallMessageCall(event.conversationId, event.senderUserId)),
            arrangement.duringCallMessages,
        )
    }

    @Test
    fun givenEventFailsToBeConsumed_whenHandlerInvoked_thenErrorIsPropagated() = runTest {
        val event = conversationProtocolEvent()
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, handler) = arrange {
            withUpdateProtocolReturning(Either.Left(failure))
        }

        val result = handler.handle(arrangement.transactionContext, event)

        assertSame(failure, assertIs<Either.Left<CoreFailure>>(result).value)
        assertEquals(
            UpdateCall(arrangement.transactionContext, event.conversationId, event.protocol, localOnly = true),
            arrangement.updateConversationProtocol.calls.single(),
        )
        assertEquals(listOf("update"), arrangement.callOrder)
    }

    @Test
    fun givenStaleProtocolEventForDeletedConversation_whenBackendReturnsNoConversation_thenEventIsSkipped() = runTest {
        val event = conversationProtocolEvent()
        val failure = noConversationFailure()
        val (arrangement, handler) = arrange {
            withUpdateProtocolReturning(Either.Left(failure))
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, event))

        assertTrue(arrangement.protocolMessages.isEmpty())
        assertTrue(arrangement.duringCallMessages.isEmpty())
        assertEquals(0, arrangement.establishedCalls.callCount)
        assertEquals(listOf("update"), arrangement.callOrder)
    }

    @Test
    fun givenProtocolEvent_whenBackendReturnsOtherServerError_thenErrorIsPropagated() = runTest {
        val event = conversationProtocolEvent()
        val failure = serverFailure(label = "generic-test-error")
        val (arrangement, handler) = arrange {
            withUpdateProtocolReturning(Either.Left(failure))
        }

        val result = handler.handle(arrangement.transactionContext, event)

        assertSame(failure, assertIs<Either.Left<CoreFailure>>(result).value)
        assertEquals(listOf("update"), arrangement.callOrder)
    }

    @Test
    fun givenProtocolWasNotAlreadyUpdated_whenHandlerInvoked_thenSystemMessageIsInserted() = runTest {
        val event = conversationProtocolEvent()
        val (arrangement, handler) = arrange {
            withUpdateProtocolReturning(Either.Right(true))
            withoutAnyEstablishedCall()
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, event))

        assertEquals(
            UpdateCall(arrangement.transactionContext, event.conversationId, event.protocol, localOnly = true),
            arrangement.updateConversationProtocol.calls.single(),
        )
        assertEquals(1, arrangement.protocolMessages.size)
    }

    @Test
    fun givenProtocolWasAlreadyUpdated_whenHandlerInvoked_thenSystemMessageIsNotInserted() = runTest {
        val event = conversationProtocolEvent()
        val (arrangement, handler) = arrange {
            withUpdateProtocolReturning(Either.Right(false))
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, event))

        assertTrue(arrangement.protocolMessages.isEmpty())
        assertTrue(arrangement.duringCallMessages.isEmpty())
        assertEquals(0, arrangement.establishedCalls.callCount)
        assertEquals(listOf("update"), arrangement.callOrder)
    }

    @Test
    fun givenMixedProtocolUpdateDuringCall_whenHandling_thenOperationsRunInExactOrderWithExactArguments() = runTest {
        val event = conversationProtocolEvent()
        val (arrangement, handler) = arrange {
            withUpdateProtocolReturning(Either.Right(true))
            withEstablishedCall()
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, event))

        assertEquals(listOf("update", "protocolMessage", "callQuery", "duringCallMessage"), arrangement.callOrder)
        assertEquals(
            UpdateCall(arrangement.transactionContext, event.conversationId, event.protocol, localOnly = true),
            arrangement.updateConversationProtocol.calls.single(),
        )
        assertEquals(
            ProtocolMessageCall(event.conversationId, event.senderUserId, event.protocol),
            arrangement.protocolMessages.single(),
        )
        assertEquals(
            DuringCallMessageCall(event.conversationId, event.senderUserId),
            arrangement.duringCallMessages.single(),
        )
    }

    @Test
    fun givenNonMixedProtocolWasUpdated_whenHandling_thenCallsAreStillQueriedButDuringCallMessageIsSkipped() = runTest {
        val event = conversationProtocolEvent(protocol = Conversation.Protocol.MLS)
        val (arrangement, handler) = arrange {
            withUpdateProtocolReturning(Either.Right(true))
            withEstablishedCall()
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, event))

        assertEquals(1, arrangement.establishedCalls.callCount)
        assertEquals(listOf("update", "protocolMessage", "callQuery"), arrangement.callOrder)
        assertTrue(arrangement.duringCallMessages.isEmpty())
    }

    @Test
    fun givenCallStateCallbackReadsAFlow_whenHandling_thenOnlyTheFirstEmissionIsObserved() = runTest {
        val event = conversationProtocolEvent()
        val arrangement = Arrangement().withUpdateProtocolReturning(Either.Right(true))
        val emissions = mutableListOf<Boolean>()
        val handler = arrangement.handlerWithCallState {
            flow {
                emissions += true
                emit(true)
                emissions += false
                emit(false)
            }.first()
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, event))

        assertEquals(listOf(true), emissions)
        assertEquals(1, arrangement.duringCallMessages.size)
    }

    @Test
    fun givenOtherInvalidRequestLabel_whenHandling_thenItIsNotClassifiedAsNoConversation() = runTest {
        val event = conversationProtocolEvent()
        val failure = serverFailure(label = "no-conversation-code")
        val (arrangement, handler) = arrange {
            withUpdateProtocolReturning(Either.Left(failure))
        }

        val result = handler.handle(arrangement.transactionContext, event)

        assertSame(failure, assertIs<Either.Left<CoreFailure>>(result).value)
        assertEquals(listOf("update"), arrangement.callOrder)
    }

    @Test
    fun givenAnyDependencyThrows_whenHandling_thenSameExceptionEscapesAndLaterWorkIsSkipped() = runTest {
        FailureStage.entries.forEach { stage ->
            assertEscapingFailure(stage, IllegalStateException("$stage failed"))
        }
    }

    @Test
    fun givenAnyDependencyCancels_whenHandling_thenSameCancellationEscapesAndLaterWorkIsSkipped() = runTest {
        FailureStage.entries.forEach { stage ->
            assertEscapingFailure(stage, CancellationException("$stage cancelled"))
        }
    }

    private suspend fun assertEscapingFailure(stage: FailureStage, expected: Throwable) {
        val (arrangement, handler) = arrange {
            withUpdateProtocolReturning(
                Either.Right(true),
                expected.takeIf { stage == FailureStage.UPDATE },
            )
            withProtocolMessageThrowing(expected.takeIf { stage == FailureStage.PROTOCOL_MESSAGE })
            withEstablishedCall(expected.takeIf { stage == FailureStage.CALL_QUERY })
            withDuringCallMessageThrowing(expected.takeIf { stage == FailureStage.DURING_CALL_MESSAGE })
        }

        val actual = try {
            handler.handle(arrangement.transactionContext, conversationProtocolEvent())
            fail("Expected $expected to escape from $stage")
        } catch (actual: Throwable) {
            actual
        }

        assertSame(expected, actual)
        assertEquals(FailureStage.entries.take(stage.ordinal + 1).map { it.callName }, arrangement.callOrder)
    }

    private class Arrangement {
        val transactionContext = mock<CryptoTransactionContext>()
        val systemMessageInserter = mock<SystemMessageInserter>(mode = MockMode.autoUnit)
        val callOrder = mutableListOf<String>()
        val protocolMessages = mutableListOf<ProtocolMessageCall>()
        val duringCallMessages = mutableListOf<DuringCallMessageCall>()
        val updateConversationProtocol = UpdateConversationProtocolRecorder(callOrder)
        val establishedCalls = EstablishedCallsRecorder(callOrder)
        private var protocolMessageThrowable: Throwable? = null
        private var duringCallMessageThrowable: Throwable? = null

        private val handler: ProtocolUpdateEventHandler = handlerWithCallState(establishedCalls::invoke)

        init {
            everySuspend {
                systemMessageInserter.insertProtocolChangedSystemMessage(any(), any(), any())
            } calls {
                callOrder += "protocolMessage"
                protocolMessages += ProtocolMessageCall(
                    conversationId = it.args[0] as ConversationId,
                    senderUserId = it.args[1] as UserId,
                    protocol = it.args[2] as Conversation.Protocol,
                )
                protocolMessageThrowable?.let { throwable -> throw throwable }
            }
            everySuspend {
                systemMessageInserter.insertProtocolChangedDuringACallSystemMessage(any(), any())
            } calls {
                callOrder += "duringCallMessage"
                duringCallMessages += DuringCallMessageCall(
                    conversationId = it.args[0] as ConversationId,
                    senderUserId = it.args[1] as UserId,
                )
                duringCallMessageThrowable?.let { throwable -> throw throwable }
            }
        }

        fun withUpdateProtocolReturning(result: Either<CoreFailure, Boolean>, throwable: Throwable? = null) = apply {
            updateConversationProtocol.result = result
            updateConversationProtocol.throwable = throwable
        }

        fun withEstablishedCall(throwable: Throwable? = null) = apply {
            establishedCalls.hasEstablishedCalls = true
            establishedCalls.throwable = throwable
        }

        fun withoutAnyEstablishedCall() = apply {
            establishedCalls.hasEstablishedCalls = false
        }

        fun withProtocolMessageThrowing(throwable: Throwable?) = apply {
            protocolMessageThrowable = throwable
        }

        fun withDuringCallMessageThrowing(throwable: Throwable?) = apply {
            duringCallMessageThrowable = throwable
        }

        fun handlerWithCallState(hasEstablishedCalls: suspend () -> Boolean): ProtocolUpdateEventHandler =
            ProtocolUpdateEventHandlerImpl(
                systemMessageInserter = systemMessageInserter,
                hasEstablishedCalls = hasEstablishedCalls,
                updateConversationProtocol = updateConversationProtocol::invoke,
            )

        fun arrange() = this to handler
    }

    private class UpdateConversationProtocolRecorder(
        private val callOrder: MutableList<String>,
    ) {
        var result: Either<CoreFailure, Boolean> = Either.Right(true)
        var throwable: Throwable? = null
        val calls = mutableListOf<UpdateCall>()

        suspend fun invoke(
            transactionContext: CryptoTransactionContext,
            conversationId: ConversationId,
            protocol: Conversation.Protocol,
            localOnly: Boolean,
        ): Either<CoreFailure, Boolean> {
            callOrder += "update"
            calls += UpdateCall(transactionContext, conversationId, protocol, localOnly)
            throwable?.let { throw it }
            return result
        }
    }

    private class EstablishedCallsRecorder(
        private val callOrder: MutableList<String>,
    ) {
        var hasEstablishedCalls: Boolean = false
        var throwable: Throwable? = null
        var callCount: Int = 0

        suspend fun invoke(): Boolean {
            callOrder += "callQuery"
            callCount++
            throwable?.let { throw it }
            return hasEstablishedCalls
        }
    }

    private data class UpdateCall(
        val transactionContext: CryptoTransactionContext,
        val conversationId: ConversationId,
        val protocol: Conversation.Protocol,
        val localOnly: Boolean,
    )

    private data class ProtocolMessageCall(
        val conversationId: ConversationId,
        val senderUserId: UserId,
        val protocol: Conversation.Protocol,
    )

    private data class DuringCallMessageCall(
        val conversationId: ConversationId,
        val senderUserId: UserId,
    )

    private enum class FailureStage(val callName: String) {
        UPDATE("update"),
        PROTOCOL_MESSAGE("protocolMessage"),
        CALL_QUERY("callQuery"),
        DURING_CALL_MESSAGE("duringCallMessage"),
    }

    private companion object {
        val CONVERSATION_ID = ConversationId("conversation-id", "wire.example")
        val SENDER_USER_ID = UserId("sender-id", "wire.example")

        fun conversationProtocolEvent(
            protocol: Conversation.Protocol = Conversation.Protocol.MIXED,
        ) = Event.Conversation.ConversationProtocol(
            id = "event-id",
            conversationId = CONVERSATION_ID,
            protocol = protocol,
            senderUserId = SENDER_USER_ID,
        )

        fun noConversationFailure(): NetworkFailure.ServerMiscommunication = serverFailure(label = "no-conversation")

        fun serverFailure(label: String): NetworkFailure.ServerMiscommunication =
            NetworkFailure.ServerMiscommunication(
                KaliumException.InvalidRequestError(
                    GenericAPIErrorResponse(
                        code = 404,
                        message = "test error",
                        label = label,
                    )
                )
            )

        fun arrange(configuration: Arrangement.() -> Unit): Pair<Arrangement, ProtocolUpdateEventHandler> =
            Arrangement().apply(configuration).arrange()
    }
}
