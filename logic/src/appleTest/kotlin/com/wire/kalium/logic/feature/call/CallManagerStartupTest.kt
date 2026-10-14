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
package com.wire.kalium.logic.feature.call

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.wire.kalium.logger.AppleCallTrace
import com.wire.kalium.logger.CallTraceRole
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import kotlin.test.AfterTest
import kotlin.test.assertTrue
import com.wire.kalium.calling.AppleAvsBridge
import com.wire.kalium.calling.AppleAvsCallbacks
import com.wire.kalium.calling.CallClosedReason
import com.wire.kalium.calling.ConversationTypeCalling
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.call.CallMetadata
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.call.mapper.CallMapperImpl
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestCall
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.util.KaliumDispatcher
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CallManagerStartupTest {

    @AfterTest
    fun resetTrace() { AppleCallTrace.configure(KaliumLogger.Config.DISABLED, CallTraceRole.APP) }

    @Test
    fun rejectedStartTraceLinksNativeCodeToFailedOperationWithoutExceptionText() = runTest {
        val lines = captureTrace()
        val f = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        every { f.avs.startCall(any(), any(), any(), any(), any(), any()) } returns 5
        val manager = f.manager()
        assertFailsWith<CallStartException> { manager.start() }
        val requested = lines.single { "event=START " in it && "result=REQUESTED" in it }
        val operation = requested.substringAfter("operation=").substringBefore(' ')
        assertTrue(lines.any { "event=AVS_START " in it && "operation=$operation" in it && "result=FAILED code=5" in it })
        assertTrue(lines.any { "event=START " in it && "operation=$operation" in it && "result=FAILED" in it })
        assertTrue(lines.none { "AVS rejected startup" in it })
        manager.cancelJobs()
    }

    @Test
    fun malformedCallbackDiagnosticLookupDoesNotThrowThroughNativeBoundary() = runTest {
        val lines = captureTrace()
        val f = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        val manager = f.manager()
        manager.start()
        every { f.qualified.fromStringToQualifiedID("malformed") } calls { error("private malformed identity") }
        f.callbacks.onAnsweredCall("malformed")
        f.callbacks.onEstablishedCall("malformed", null, null)
        runCurrent()
        assertTrue(lines.any { "event=AVS_ANSWERED" in it })
        assertTrue(lines.any { "event=CALLBACK_EFFECT" in it && "result=FAILED" in it })
        assertTrue(lines.none { "private malformed identity" in it })
        manager.cancelJobs()
    }

    private fun captureTrace(): MutableList<String> {
        val lines = mutableListOf<String>()
        val writer = object : LogWriter() {
            override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) { lines += message }
        }
        AppleCallTrace.configure(KaliumLogger.Config(KaliumLogLevel.INFO, listOf(writer)), CallTraceRole.APP)
        return lines
    }

    @Test
    fun acceptedStartKeepsRowStarted() = runTest {
        val f = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        val manager = f.manager()
        manager.start()
        assertEquals(CallStatus.STARTED, f.metadata?.callStatus)
        verify(VerifyMode.exactly(1)) { f.avs.startCall(any(), any(), any(), any(), any(), any()) }
        manager.cancelJobs()
    }

    @Test
    fun rejectedNativeStartClosesRowWithoutEndingOrReleasingHandle() = runTest {
        val f = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        every { f.avs.startCall(any(), any(), any(), any(), any(), any()) } returns 5
        val manager = f.manager()
        assertFailsWith<CallStartException> { manager.start() }
        assertEquals(CallStatus.CLOSED, f.metadata?.callStatus)
        verify(VerifyMode.exactly(0)) { f.avs.endCall(any(), any()) }
        verifySuspend(VerifyMode.exactly(0)) { f.avs.releaseUserHandle(any()) }
        manager.cancelJobs()
    }

    @Test
    fun missingHandleCreatesNoCallRow() = runTest {
        val f = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        every { f.avs.userHandle(any(), any(), any()) } returns null
        val manager = f.manager()
        assertFailsWith<CallStartException> { manager.start() }
        assertNull(f.metadata)
        verify(VerifyMode.exactly(0)) { f.avs.startCall(any(), any(), any(), any(), any(), any()) }
        manager.cancelJobs()
    }

    @Test
    fun mlsJoinRejectionNeverStartsAvs() = runTest {
        val f = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher(), mls = true)
        everySuspend { f.repository.joinMlsConference(any(), any(), any()) } returns Either.Left(CoreFailure.Unknown(null))
        val manager = f.manager()
        assertFailsWith<CallStartException> { manager.start() }
        assertEquals(CallStatus.CLOSED, f.metadata?.callStatus)
        verify(VerifyMode.exactly(0)) { f.avs.startCall(any(), any(), any(), any(), any(), any()) }
        verifySuspend(VerifyMode.exactly(0)) { f.repository.leaveMlsConference(any()) }
        manager.cancelJobs()
    }

    @Test
    fun nativeRejectionAfterMlsJoinLeavesConference() = runTest {
        val f = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher(), mls = true)
        every { f.avs.startCall(any(), any(), any(), any(), any(), any()) } returns -1
        val manager = f.manager()
        assertFailsWith<CallStartException> { manager.start() }
        verifySuspend(VerifyMode.exactly(1)) { f.repository.leaveMlsConference(CONVERSATION) }
        assertEquals(CallStatus.CLOSED, f.metadata?.callStatus)
        manager.cancelJobs()
    }

    @Test
    fun startWaitsForReadinessBeforeCreatingRow() = runTest {
        val f = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher(), ready = false)
        val manager = f.manager()
        val start = launch { manager.start() }
        runCurrent()
        assertNull(f.metadata)
        verify(VerifyMode.exactly(0)) { f.avs.startCall(any(), any(), any(), any(), any(), any()) }
        f.callbacks.onReady(0)
        start.join()
        assertEquals(CallStatus.STARTED, f.metadata?.callStatus)
        verify(VerifyMode.exactly(1)) { f.avs.startCall(any(), any(), any(), any(), any(), any()) }
        manager.cancelJobs()
    }

    @Test
    fun cancelledMeetingDoesNotPersistMissedCall() = verifyClose(CallClosedReason.CANCELLED, CallStatus.MISSED)

    @Test
    fun meetingClosedWithoutJoiningDoesNotPersistMissedCall() = verifyClose(CallClosedReason.NORMAL, CallStatus.CLOSED)

    @Test
    fun callAnsweredElsewhereDoesNotPersistMissedCallWhenClosed() = runTest {
        for (type in listOf(Conversation.Type.OneOnOne, Conversation.Type.Group.Regular)) {
            val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
            fixture.metadata = TestCall.oneOnOneCallMetadata().copy(callStatus = CallStatus.INCOMING, conversationType = type)
            val manager = fixture.manager()
            try {
                manager.waitUntilInitialized()
                fixture.callbacks.onClosedCall(CallClosedReason.ANSWERED_ELSEWHERE.avsValue, "call@example.com", 1u, "caller@example.com", "client")
                runCurrent()

                assertEquals(true, fixture.metadata?.callAnsweredElsewhere)
                assertEquals(if (type is Conversation.Type.Group) CallStatus.STILL_ONGOING else CallStatus.CLOSED, fixture.metadata?.callStatus)
                verifySuspend(VerifyMode.not) { fixture.repository.persistMissedCall(CONVERSATION) }

                fixture.callbacks.onClosedCall(CallClosedReason.NORMAL.avsValue, "call@example.com", 1u, "caller@example.com", "client")
                runCurrent()

                assertEquals(CallStatus.CLOSED, fixture.metadata?.callStatus)
                verifySuspend(VerifyMode.not) { fixture.repository.persistMissedCall(CONVERSATION) }
            } finally {
                manager.cancelJobs()
            }
        }
    }

    private fun verifyClose(reason: CallClosedReason, expectedStatus: CallStatus) = runTest {
        val cases = listOf(
            Conversation.Type.Group.Meeting to false,
            Conversation.Type.Group.Regular to true,
            Conversation.Type.OneOnOne to true,
            null to (reason == CallClosedReason.CANCELLED)
        )
        for ((type, persistMissedCall) in cases) {
            val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
            fixture.metadata = type?.let {
                TestCall.oneOnOneCallMetadata().copy(
                    callStatus = CallStatus.INCOMING,
                    establishedTime = null,
                    conversationType = it,
                    protocol = if (it == Conversation.Type.Group.Meeting) Conversation.ProtocolInfo.MLS(
                        GroupID("meeting"),
                        Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                        1uL,
                        Instant.DISTANT_PAST,
                        CipherSuite.fromTag(1)
                    ) else Conversation.ProtocolInfo.Proteus
                )
            }
            val manager = fixture.manager()
            try {
                manager.waitUntilInitialized()
                fixture.callbacks.onClosedCall(reason.avsValue, "call@example.com", 1u, "caller@example.com", "client")
                runCurrent()

                verifySuspend(VerifyMode.exactly(if (persistMissedCall) 1 else 0)) {
                    fixture.repository.persistMissedCall(CONVERSATION)
                }
                verifySuspend(VerifyMode.exactly(1)) {
                    fixture.repository.updateCallStatusById(CONVERSATION, expectedStatus)
                }
                if (type == Conversation.Type.Group.Meeting) {
                    verifySuspend(VerifyMode.exactly(1)) { fixture.repository.leaveMlsConference(CONVERSATION) }
                }
            } finally {
                manager.cancelJobs()
            }
        }
    }

    @Test
    fun acceptedAnswerPreservesExistingConference() = runTest {
        val f = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        f.metadata = TestCall.oneOnOneCallMetadata().copy(callStatus = CallStatus.STILL_ONGOING)
        val manager = f.manager()
        manager.answerCall(CONVERSATION, false, false)
        assertEquals(CallStatus.STILL_ONGOING, f.metadata?.callStatus)
        verify(VerifyMode.exactly(1)) { f.avs.answerCall(any(), any(), any(), any()) }
        manager.cancelJobs()
    }

    @Test
    fun rejectedAnswerPropagatesWithoutClosingRemoteConference() = runTest {
        val f = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        f.metadata = TestCall.oneOnOneCallMetadata().copy(callStatus = CallStatus.STILL_ONGOING)
        every { f.avs.answerCall(any(), any(), any(), any()) } returns -1
        val manager = f.manager()
        assertFailsWith<IllegalStateException> { manager.answerCall(CONVERSATION, false, false) }
        assertEquals(CallStatus.STILL_ONGOING, f.metadata?.callStatus)
        verify(VerifyMode.exactly(0)) { f.avs.endCall(any(), any()) }
        manager.cancelJobs()
    }

    @Test
    fun rejectedMlsAnswerPropagatesWithoutSubmittingAvs() = runTest {
        val f = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        f.metadata = TestCall.oneOnOneCallMetadata().copy(
            protocol = Conversation.ProtocolInfo.MLS(
                GroupID("group"), Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                1uL, Instant.DISTANT_PAST, CipherSuite.fromTag(1)
            )
        )
        everySuspend { f.repository.joinMlsConference(any(), any(), any()) } returns Either.Left(CoreFailure.Unknown(null))
        val manager = f.manager()
        assertFailsWith<IllegalStateException> { manager.answerCall(CONVERSATION, false, false) }
        verify(VerifyMode.exactly(0)) { f.avs.answerCall(any(), any(), any(), any()) }
        verifySuspend(VerifyMode.exactly(0)) { f.repository.leaveMlsConference(any()) }
        manager.cancelJobs()
    }

    private suspend fun CallManager.start() = startCall(CONVERSATION, CallType.AUDIO, ConversationTypeCalling.OneOnOne, false, false)

    private class Fixture(
        private val dispatcher: KaliumDispatcher,
        private val mls: Boolean = false,
        private val ready: Boolean = true
    ) {
        val avs = mock<AppleAvsBridge>(MockMode.autoUnit)
        val repository = mock<CallRepository>(MockMode.autoUnit)
        var metadata: CallMetadata? = null
        lateinit var callbacks: AppleAvsCallbacks
        private val client = mock<CurrentClientIdProvider>()
        private val federated = mock<FederatedIdMapper>()
        val qualified = mock<QualifiedIdMapper>()
        private val network = mock<NetworkStateObserver>()

        init {
            everySuspend { client() } returns ClientId("client").right()
            everySuspend { federated.parseToFederatedId(any<QualifiedID>()) } calls {
                val id = it.arg<QualifiedID>(0)
                "${id.value}@${id.domain}"
            }
            every { qualified.fromStringToQualifiedID(any()) } returns CONVERSATION
            every { avs.userHandle(any(), any(), any()) } calls {
                callbacks = it.arg(2)
                if (ready) callbacks.onReady(0)
                HANDLE
            }
            every { avs.answerCall(any(), any(), any(), any()) } returns 0
            every { avs.startCall(any(), any(), any(), any(), any(), any()) } returns 0
            every { repository.getCallMetadata(any()) } calls { metadata }
            everySuspend { repository.createCall(any(), any(), any(), any(), any(), any(), any()) } calls {
                metadata = TestCall.oneOnOneCallMetadata().copy(
                    callStatus = it.arg(2),
                    protocol = if (mls) Conversation.ProtocolInfo.MLS(
                        GroupID("group"), Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                        1uL, Instant.DISTANT_PAST, CipherSuite.fromTag(1)
                    ) else Conversation.ProtocolInfo.Proteus
                )
            }
            everySuspend { repository.updateIsAnsweredElsewhere(any()) } calls {
                metadata = metadata?.copy(callAnsweredElsewhere = true)
            }
            everySuspend { repository.updateCallStatusById(any(), any()) } calls {
                metadata = metadata?.copy(callStatus = it.arg(1))
            }
            everySuspend { repository.joinMlsConference(any(), any(), any()) } calls {
                it.arg<suspend () -> Unit>(1)()
                Unit.right()
            }
            every { network.observeNetworkState() } returns MutableStateFlow(NetworkState.ConnectedWithInternet)
        }

        fun manager() = CallManagerImpl(
            repository, client, mock(), mock(), CallMapperImpl(qualified), federated, qualified,
            mock(), mock(MockMode.autoUnit), mock(MockMode.autoUnit), network, mock(), mock(), KaliumConfigs(),
            mock(MockMode.autoUnit), mock(MockMode.autoUnit), mock(MockMode.autoUnit), UserId("self", "example.com"),
            kaliumDispatchers = dispatcher, avs = avs
        )
    }

    private companion object {
        const val HANDLE = 42u
        val CONVERSATION = ConversationId("call", "example.com")
    }
}
