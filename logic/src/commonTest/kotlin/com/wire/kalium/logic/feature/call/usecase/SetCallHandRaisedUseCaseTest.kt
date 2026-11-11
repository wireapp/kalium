/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.call.CallHandIdentity
import com.wire.kalium.logic.data.call.CallHandState
import com.wire.kalium.logic.data.call.CallMetadata
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.messaging.sending.MessageSender
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class SetCallHandRaisedUseCaseTest {
    @Test
    fun confirmsOnlyAfterSendAndCarriesDeviceIdentity() = runTest {
        val arrangement = Arrangement(this)
        val completed = CompletableDeferred<Unit>()
        everySuspend { arrangement.sender.sendMessage(any(), any()) } calls { invocation ->
            val message = invocation.args[0] as Message.Signaling
            assertEquals(TestUser.SELF.id, message.senderUserId)
            assertEquals(TestClient.CLIENT_ID, message.senderClientId)
            assertEquals(MessageContent.InCallHandRaise(true), message.content)
            completed.await()
            Either.Right(Unit)
        }
        val result = async { arrangement.useCase(TestConversation.ID, true, "first") }
        runCurrent()
        verify(VerifyMode.not) { arrangement.calls.updateHand(any(), any(), any(), any(), any(), any()) }
        completed.complete(Unit)
        assertIs<SetCallHandRaisedUseCase.Result.Success>(result.await())
        verify(VerifyMode.exactly(1)) { arrangement.calls.updateHand(any(), any(), true, any(), any(), "first") }
    }

    @Test
    fun failurePreservesStateAndAllowsExplicitRetry() = runTest {
        val arrangement = Arrangement(this)
        everySuspend { arrangement.sender.sendMessage(any(), any()) } returns
            Either.Left(NetworkFailure.NoNetworkConnection(null))
        assertIs<SetCallHandRaisedUseCase.Result.Failure>(arrangement.useCase(TestConversation.ID, true, "first"))
        verify(VerifyMode.not) { arrangement.calls.updateHand(any(), any(), any(), any(), any(), any()) }
        everySuspend { arrangement.sender.sendMessage(any(), any()) } returns Either.Right(Unit)
        assertIs<SetCallHandRaisedUseCase.Result.Success>(arrangement.useCase(TestConversation.ID, true, "first"))
    }

    @Test
    fun lateSendCompletionDoesNotUpdateReplacementCall() = runTest {
        val arrangement = Arrangement(this)
        everySuspend { arrangement.sender.sendMessage(any(), any()) } calls {
            arrangement.metadata = arrangement.metadata.copy(
                handState = CallHandState.start("replacement", Instant.fromEpochSeconds(2), setOf(arrangement.self))
            )
            Either.Right(Unit)
        }
        assertIs<SetCallHandRaisedUseCase.Result.NotJoined>(arrangement.useCase(TestConversation.ID, true, "first"))
        verify { arrangement.calls.updateHand(any(), any(), any(), any(), any(), "first") }
        assertTrue(arrangement.metadata.handState!!.raised.isEmpty())
    }

    @Test
    fun departureBeforeAtomicConfirmationDoesNotReportSuccess() = runTest {
        val arrangement = Arrangement(this)
        everySuspend { arrangement.sender.sendMessage(any(), any()) } returns Either.Right(Unit)
        every { arrangement.calls.updateHand(any(), any(), any(), any(), any(), any()) } returns false
        assertIs<SetCallHandRaisedUseCase.Result.NotJoined>(arrangement.useCase(TestConversation.ID, true, "first"))
    }

    @Test
    fun duplicateLowerDoesNotSendAndMissingParticipationIsRejected() = runTest {
        val arrangement = Arrangement(this)
        assertIs<SetCallHandRaisedUseCase.Result.Success>(arrangement.useCase(TestConversation.ID, false, "first"))
        arrangement.metadata = arrangement.metadata.copy(callStatus = CallStatus.STILL_ONGOING, handState = null)
        assertIs<SetCallHandRaisedUseCase.Result.NotJoined>(arrangement.useCase(TestConversation.ID, true, "first"))
        verifySuspend(VerifyMode.not) { arrangement.sender.sendMessage(any(), any()) }
    }

    @Test
    fun overlappingCommandsAreNotQueued() = runTest {
        val arrangement = Arrangement(this)
        val completed = CompletableDeferred<Unit>()
        everySuspend { arrangement.sender.sendMessage(any(), any()) } calls {
            completed.await()
            Either.Right(Unit)
        }
        val first = async { arrangement.useCase(TestConversation.ID, true, "first") }
        runCurrent()
        assertIs<SetCallHandRaisedUseCase.Result.Busy>(arrangement.useCase(TestConversation.ID, false, "first"))
        completed.complete(Unit)
        assertIs<SetCallHandRaisedUseCase.Result.Success>(first.await())
    }

    @Test
    fun callerDisposalDoesNotCancelSessionOwnedSend() = runTest {
        val arrangement = Arrangement(this)
        val completed = CompletableDeferred<Unit>()
        everySuspend { arrangement.sender.sendMessage(any(), any()) } calls {
            completed.await()
            Either.Right(Unit)
        }
        val caller = async { arrangement.useCase(TestConversation.ID, true, "first") }
        runCurrent()
        caller.cancelAndJoin()
        completed.complete(Unit)
        runCurrent()
        verify(VerifyMode.exactly(1)) { arrangement.calls.updateHand(any(), any(), true, any(), any(), "first") }
    }

    @Test
    fun suspendedClientLookupCannotSendIntoReplacementParticipation() = runTest {
        val arrangement = Arrangement(this)
        val lookup = CompletableDeferred<Unit>()
        everySuspend { arrangement.client() } calls {
            lookup.await()
            Either.Right(TestClient.CLIENT_ID)
        }
        val result = async { arrangement.useCase(TestConversation.ID, true, "first") }
        runCurrent()
        arrangement.metadata = arrangement.metadata.copy(
            handState = CallHandState.start("replacement", Instant.fromEpochSeconds(2), setOf(arrangement.self))
        )
        lookup.complete(Unit)
        assertIs<SetCallHandRaisedUseCase.Result.NotJoined>(result.await())
        verifySuspend(VerifyMode.not) { arrangement.sender.sendMessage(any(), any()) }
        assertTrue(arrangement.metadata.handState!!.raised.isEmpty())
    }

    private class Arrangement(scope: CoroutineScope) {
        val self = CallHandIdentity(TestUser.SELF.id, TestClient.CLIENT_ID.value)
        val sender = mock<MessageSender>(MockMode.autoUnit)
        val calls = mock<CallRepository>(MockMode.autoUnit)
        val client = mock<CurrentClientIdProvider>()
        var metadata = CallMetadata(
            callerId = TestUser.SELF.id, isMuted = false, isCameraOn = false, isCbrEnabled = false,
            conversationName = "Group", conversationType = Conversation.Type.Group.Regular,
            callerName = null, callerTeamName = null, callStatus = CallStatus.ESTABLISHED,
            protocol = Conversation.ProtocolInfo.Proteus,
            handState = CallHandState.start("first", Instant.fromEpochSeconds(1), setOf(self)),
        )
        val useCase = SetCallHandRaisedUseCase(TestUser.SELF.id, client, sender, calls, scope)
        init {
            everySuspend { client() } returns Either.Right(TestClient.CLIENT_ID)
            every { calls.getCallMetadata(any()) } calls { metadata }
            every { calls.updateHand(any(), any(), any(), any(), any(), any()) } calls { invocation ->
                metadata.callStatus == CallStatus.ESTABLISHED && metadata.handState?.generation == invocation.args[5]
            }
        }
    }
}
