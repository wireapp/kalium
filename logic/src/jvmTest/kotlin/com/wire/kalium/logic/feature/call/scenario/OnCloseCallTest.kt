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
package com.wire.kalium.logic.feature.call.scenario

import com.wire.kalium.calling.CallClosedReason
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logic.data.call.CallMetadata
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.feature.call.usecase.CreateAndPersistRecentlyEndedCallMetadataUseCase
import com.wire.kalium.logic.framework.TestCall
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.verify.VerifyMode
import dev.mokkery.matcher.any
import dev.mokkery.verifySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.Instant
import org.junit.Test

class OnCloseCallTest {
    private val testScope = TestScope()
    private val time: Uint32_t = Uint32_t()

    @Test
    fun givenCloseReasonIsCanceled_whenOnCloseCallBackHappens_thenPersistMissedCallAndUpdateStatus() =
        testScope.runTest {
            val (arrangement, onCloseCall) = Arrangement(testScope).arrange()
            val reason = CallClosedReason.CANCELLED.avsValue

            onCloseCall.onClosedCall(
                reason,
                conversationIdString,
                time,
                userIdString,
                clientId,
                null
            )
            yield()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.callRepository.persistMissedCall(eq(conversationId))
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.MISSED))
            }

            verifySuspend(VerifyMode.not) {
                arrangement.callRepository.leaveMlsConference(eq(conversationId))
            }
        }

    @Test
    fun givenCloseReasonIsRejected_whenOnCloseCallBackHappens_thenDoNotPersistMissedCallAndUpdateStatus() =
        testScope.runTest {
            val (arrangement, onCloseCall) = Arrangement(testScope).arrange()
            val reason = CallClosedReason.REJECTED.avsValue

            onCloseCall.onClosedCall(
                reason,
                conversationIdString,
                time,
                userIdString,
                clientId,
                null
            )
            yield()

            verifySuspend(VerifyMode.not) {
                arrangement.callRepository.persistMissedCall(eq(conversationId))
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.REJECTED))
            }

            verifySuspend(VerifyMode.not) {
                arrangement.callRepository.leaveMlsConference(eq(conversationId))
            }
        }

    @Test
    fun givenAStartedGroupCall_whenOnCloseCallBackHappens_thenDoNotPersistMissedCallAndUpdateStatus() =
        testScope.runTest {
            val groupCall = callMetadata.copy(
                callStatus = CallStatus.STARTED,
                conversationType = Conversation.Type.Group.Regular,
                establishedTime = null,
            )
            val (arrangement, onCloseCall) = Arrangement(testScope)
                .withGetCallMetadata(groupCall)
                .arrange()
            val reason = CallClosedReason.TIMEOUT_ECONN.avsValue

            onCloseCall.onClosedCall(
                reason,
                conversationIdString,
                time,
                userIdString,
                clientId,
                null
            )
            yield()

            verifySuspend(VerifyMode.not) {
                arrangement.callRepository.persistMissedCall(eq(conversationId))
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.CLOSED))
            }

            verifySuspend(VerifyMode.not) {
                arrangement.callRepository.leaveMlsConference(eq(conversationId))
            }
        }

    @Test
    fun givenAnIncomingGroupCall_whenOnCloseCallBackHappens_thenPersistMissedCallAndUpdateStatus() =
        testScope.runTest {
            val incomingCall = callMetadata.copy(
                callStatus = CallStatus.INCOMING,
                conversationType = Conversation.Type.Group.Regular
            )
            val (arrangement, onCloseCall) = Arrangement(testScope)
                .withGetCallMetadata(incomingCall)
                .arrange()
            val reason = CallClosedReason.NORMAL.avsValue

            onCloseCall.onClosedCall(
                reason,
                conversationIdString,
                time,
                userIdString,
                clientId,
                null
            )
            yield()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.callRepository.persistMissedCall(eq(conversationId))
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.CLOSED))
            }

            verifySuspend(VerifyMode.not) {
                arrangement.callRepository.leaveMlsConference(eq(conversationId))
            }
        }

    @Test
    fun givenCurrentCallClosedInternally_whenOnCloseCallBackHappens_thenDoNotPersistMissedCallAndUpdateStatus() =
        testScope.runTest {
            val closedInternallyCall = callMetadata.copy(
                callStatus = CallStatus.CLOSED_INTERNALLY,
                conversationType = Conversation.Type.Group.Regular
            )
            val (arrangement, onCloseCall) = Arrangement(testScope)
                .withGetCallMetadata(closedInternallyCall)
                .arrange()
            val reason = CallClosedReason.NORMAL.avsValue

            onCloseCall.onClosedCall(
                reason,
                conversationIdString,
                time,
                userIdString,
                clientId,
                null
            )
            yield()

            verifySuspend(VerifyMode.not) {
                arrangement.callRepository.persistMissedCall(eq(conversationId))
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.CLOSED))
            }

            verifySuspend(VerifyMode.not) {
                arrangement.callRepository.leaveMlsConference(eq(conversationId))
            }
        }

    @Test
    fun givenCurrentCallIsEstablished_whenOnCloseCallBackHappens_thenDoNotPersistMissedCallAndUpdateStatus() =
        testScope.runTest {
            val establishedCall = callMetadata.copy(
                callStatus = CallStatus.ESTABLISHED,
                establishedTime = "time",
                conversationType = Conversation.Type.Group.Regular
            )
            val (arrangement, onCloseCall) = Arrangement(testScope)
                .withGetCallMetadata(establishedCall)
                .arrange()
            val reason = CallClosedReason.NORMAL.avsValue

            onCloseCall.onClosedCall(
                reason,
                conversationIdString,
                time,
                userIdString,
                clientId,
                null
            )
            yield()

            verifySuspend(VerifyMode.not) {
                arrangement.callRepository.persistMissedCall(eq(conversationId))
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.CLOSED))
            }

            verifySuspend(VerifyMode.not) {
                arrangement.callRepository.leaveMlsConference(eq(conversationId))
            }
        }

    @Test
    fun givenMLSCallEndedNormally_whenOnCloseCallBackHappens_thenLeaveMlsConference() =
        testScope.runTest {
            val mlsCall = callMetadata.copy(
                protocol = Conversation.ProtocolInfo.MLS(
                    groupId = GroupID(""),
                    groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                    epoch = ULong.MAX_VALUE,
                    keyingMaterialLastUpdate = Instant.DISTANT_FUTURE,
                    cipherSuite = CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
                )
            )
            val (arrangement, onCloseCall) = Arrangement(testScope)
                .withGetCallMetadata(mlsCall)
                .arrange()
            val reason = CallClosedReason.NORMAL.avsValue

            onCloseCall.onClosedCall(
                reason,
                conversationIdString,
                time,
                userIdString,
                clientId,
                null
            )
            yield()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.CLOSED))
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.callRepository.leaveMlsConference(eq(conversationId))
            }
        }

    @Test
    fun givenDeviceOffline_whenOnCloseCallBackHappens_thenDoNotPersistMissedCall() =
        testScope.runTest {
            val (arrangement, onCloseCall) = Arrangement(testScope)
                .withNetworkState(NetworkState.NotConnected)
                .arrange()
            val reason = CallClosedReason.CANCELLED.avsValue

            onCloseCall.onClosedCall(
                reason,
                conversationIdString,
                time,
                userIdString,
                clientId,
                null
            )
            yield()

            verifySuspend(VerifyMode.not) {
                arrangement.callRepository.persistMissedCall(conversationId)
            }
        }

    @Test
    fun givenClosedCall_whenOnCloseCallInvoked_thenCreateAndPersistRecentlyEndedCallIsInvoked() =
        testScope.runTest {
            val (arrangement, onCloseCall) = Arrangement(testScope).arrange()
            val reason = CallClosedReason.CANCELLED.avsValue

            onCloseCall.onClosedCall(
                reason,
                conversationIdString,
                time,
                userIdString,
                clientId,
                null
            )
            yield()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.createAndPersistRecentlyEndedCallMetadata(any(), any())
            }
        }

    @Test
    fun givenNoCurrentSessionCall_whenOnCloseCallInvoked_thenLeaveStaleMlsConferenceIfNeeded() =
        testScope.runTest {
            val (arrangement, onCloseCall) = Arrangement(testScope)
                .withGetCallMetadata(null)
                .arrange()
            val reason = CallClosedReason.NORMAL.avsValue

            onCloseCall.onClosedCall(
                reason,
                conversationIdString,
                time,
                userIdString,
                clientId,
                null
            )
            yield()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.callRepository.leaveStaleMlsConferenceIfNeeded(eq(conversationId))
            }
        }

    private class Arrangement(val testScope: TestScope) {
        val callRepository: CallRepository = mock<CallRepository>(mode = MockMode.autoUnit)
        val networkStateObserver = mock<NetworkStateObserver>(mode = MockMode.autoUnit)
        val createAndPersistRecentlyEndedCallMetadata = mock<CreateAndPersistRecentlyEndedCallMetadataUseCase>(mode = MockMode.autoUnit)
        val qualifiedIdMapper = QualifiedIdMapper(TestUser.SELF.id)

        init {
            withGetCallMetadata(callMetadata)
            withNetworkState(NetworkState.ConnectedWithInternet)
        }

        fun arrange() = this to OnCloseCall(
            callRepository = callRepository,
            scope = testScope,
            qualifiedIdMapper = qualifiedIdMapper,
            networkStateObserver = networkStateObserver,
            createAndPersistRecentlyEndedCallMetadata = createAndPersistRecentlyEndedCallMetadata,
        )

        fun withNetworkState(networkState: NetworkState): Arrangement = apply {
            every {
                networkStateObserver.observeNetworkState()
            } returns (MutableStateFlow(networkState))
        }

        fun withGetCallMetadata(metadata: CallMetadata?): Arrangement = apply {
            every {
                callRepository.getCallMetadata(conversationId)
            } returns (metadata)
        }
    }

    companion object {
        private val conversationId = ConversationId("conversationId", "wire.com")
        private const val conversationIdString = "conversationId@wire.com"
        private const val userIdString = "userId@wire.com"
        private const val clientId = "clientId"

        private val callMetadata = CallMetadata(
            callerId = TestCall.CALLER_ID,
            isMuted = false,
            isCameraOn = false,
            isCbrEnabled = false,
            conversationName = null,
            conversationType = Conversation.Type.OneOnOne,
            callerName = null,
            callerTeamName = null,
            establishedTime = null,
            callStatus = CallStatus.INCOMING,
            protocol = Conversation.ProtocolInfo.Proteus
        )
    }
}
