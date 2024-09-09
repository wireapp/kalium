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
import com.wire.kalium.logic.data.call.CallMetadataProfile
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedIdMapperImpl
import com.wire.kalium.logic.data.call.CallHelper
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.Instant
import org.junit.Before
import org.junit.Test

class OnCloseCallTest {

    @Mock
    val callRepository = mock(CallRepository::class)

    @Mock
    val networkStateObserver = mock(NetworkStateObserver::class)

    @Mock
    val callHelper = mock(classOf<CallHelper>())

    val qualifiedIdMapper = QualifiedIdMapperImpl(TestUser.SELF.id)

    private lateinit var onCloseCall: OnCloseCall

    private val testScope = TestScope()

    private val time: Uint32_t = Uint32_t()

    @Before
    fun setUp() {
        onCloseCall = OnCloseCall(
            callRepository,
            callHelper,
            testScope,
            qualifiedIdMapper,
            networkStateObserver
        )

        every {
            callRepository.getCallMetadataProfile()
        }.returns(CallMetadataProfile(mapOf(conversationId to callMetadata)))

        every {
            networkStateObserver.observeNetworkState()
        }.returns(MutableStateFlow(NetworkState.ConnectedWithInternet))
    }

    @Test
    fun givenCloseReasonIsCanceled_whenOnCloseCallBackHappens_thenPersistMissedCallAndUpdateStatus() =
        testScope.runTest {
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

            coVerify {
                callRepository.persistMissedCall(eq(conversationId))
            }.wasInvoked(once)

            coVerify {
                callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.MISSED))
            }.wasInvoked(once)

            coVerify {
                callRepository.leaveMlsConference(eq(conversationId))
            }.wasNotInvoked()
        }

    @Test
    fun givenCloseReasonIsRejected_whenOnCloseCallBackHappens_thenDoNotPersistMissedCallAndUpdateStatus() =
        testScope.runTest {
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

            coVerify {
                callRepository.persistMissedCall(eq(conversationId))
            }.wasNotInvoked()

            coVerify {
                callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.REJECTED))
            }.wasInvoked(once)

            coVerify {
                callRepository.leaveMlsConference(eq(conversationId))
            }.wasNotInvoked()
        }

    @Test
    fun givenCloseReasonIsEndedNormally_whenOnCloseCallBackHappens_thenDoNotPersistMissedCallAndUpdateStatus() =
        testScope.runTest {

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

            coVerify {
                callRepository.persistMissedCall(eq(conversationId))
            }.wasNotInvoked()

            coVerify {
                callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.CLOSED))
            }.wasInvoked(once)

            coVerify {
                callRepository.leaveMlsConference(eq(conversationId))
            }.wasNotInvoked()
        }

    @Test
    fun givenAnIncomingGroupCall_whenOnCloseCallBackHappens_thenPersistMissedCallAndUpdateStatus() =
        testScope.runTest {
            val incomingCall = callMetadata.copy(
                callStatus = CallStatus.INCOMING,
                conversationType = Conversation.Type.GROUP
            )

            every {
                callRepository.getCallMetadataProfile()
            }.returns(CallMetadataProfile(mapOf(conversationId to incomingCall)))

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

            coVerify {
                callRepository.persistMissedCall(eq(conversationId))
            }.wasInvoked(once)

            coVerify {
                callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.CLOSED))
            }.wasInvoked(once)

            coVerify {
                callRepository.leaveMlsConference(eq(conversationId))
            }.wasNotInvoked()
        }

    @Test
    fun givenCurrentCallClosedInternally_whenOnCloseCallBackHappens_thenDoNotPersistMissedCallAndUpdateStatus() =
        testScope.runTest {
            val closedInternallyCall = callMetadata.copy(
                callStatus = CallStatus.CLOSED_INTERNALLY,
                conversationType = Conversation.Type.GROUP
            )

            every {
                callRepository.getCallMetadataProfile()
            }.returns(CallMetadataProfile(mapOf(conversationId to closedInternallyCall)))

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

            coVerify {
                callRepository.persistMissedCall(eq(conversationId))
            }.wasNotInvoked()

            coVerify {
                callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.CLOSED))
            }.wasInvoked(once)

            coVerify {
                callRepository.leaveMlsConference(eq(conversationId))
            }.wasNotInvoked()
        }

    @Test
    fun givenCurrentCallIsEstablished_whenOnCloseCallBackHappens_thenDoNotPersistMissedCallAndUpdateStatus() =
        testScope.runTest {
            val establishedCall = callMetadata.copy(
                callStatus = CallStatus.ESTABLISHED,
                establishedTime = "time",
                conversationType = Conversation.Type.GROUP
            )

            every {
                callRepository.getCallMetadataProfile()
            }.returns(CallMetadataProfile(mapOf(conversationId to establishedCall)))
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

            coVerify {
                callRepository.persistMissedCall(eq(conversationId))
            }.wasNotInvoked()

            coVerify {
                callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.CLOSED))
            }.wasInvoked(once)

            coVerify {
                callRepository.leaveMlsConference(eq(conversationId))
            }.wasNotInvoked()
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

            every {
                callRepository.getCallMetadataProfile()
            }.returns(CallMetadataProfile(mapOf(conversationId to mlsCall)))
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

            coVerify {
                callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.CLOSED))
            }.wasInvoked(once)

            coVerify {
                callRepository.leaveMlsConference(eq(conversationId))
            }.wasInvoked(once)
        }

    @Test
    fun givenDeviceOffline_whenOnCloseCallBackHappens_thenDoNotPersistMissedCall() =
        testScope.runTest {
            val reason = CallClosedReason.CANCELLED.avsValue

            every {
                networkStateObserver.observeNetworkState()
            }.returns(MutableStateFlow(NetworkState.NotConnected))

            onCloseCall.onClosedCall(
                reason,
                conversationIdString,
                time,
                userIdString,
                clientId,
                null
            )
            yield()

            coVerify {
                callRepository.persistMissedCall(conversationId)
            }.wasNotInvoked()
        }

    companion object {
        private val conversationId = ConversationId("conversationId", "wire.com")
        private const val conversationIdString = "conversationId@wire.com"
        private const val userIdString = "userId@wire.com"
        private const val clientId = "clientId"

        private val callMetadata = CallMetadata(
            isMuted = false,
            isCameraOn = false,
            isCbrEnabled = false,
            conversationName = null,
            conversationType = Conversation.Type.ONE_ON_ONE,
            callerName = null,
            callerTeamName = null,
            establishedTime = null,
            callStatus = CallStatus.INCOMING,
            protocol = Conversation.ProtocolInfo.Proteus
        )
    }
}
