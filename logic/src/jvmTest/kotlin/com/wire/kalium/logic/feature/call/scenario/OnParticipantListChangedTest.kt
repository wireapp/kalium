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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.call.CallHelper
import com.wire.kalium.logic.data.call.ParticipantMinimized
import com.wire.kalium.logic.data.call.mapper.ParticipantMapper
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapperImpl
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnParticipantListChangedTest {

    @BeforeTest
    fun setup() {
        testScope = TestScope()
    }

    @Test
    fun givenCallRepository_whenParticipantListChangedCallBackHappens_thenUpdateCallParticipantsOnce() =
        testScope.runTest {
            val (arrangement, onParticipantListChanged) = Arrangement()
                .withParticipantMapper()
                .withUserConfigRepositoryReturning(Either.Left(StorageFailure.DataNotFound))
                .arrange()

            onParticipantListChanged.onParticipantChanged(
                REMOTE_CONVERSATION_ID, data, null
            )
            yield()

            verify {
                arrangement.participantMapper.fromCallMemberToParticipantMinimized(any())
            }.wasInvoked(exactly = twice)

            coVerify {
                arrangement.callRepository.updateCallParticipants(any(), any())
            }.wasInvoked(exactly = once)
        }

    @Test
    fun givenMlsCallHelperReturnsTrue_whenParticipantListChangedCallBackHappens_thenEndCall() =
        testScope.runTest {
            val (arrangement, onParticipantListChanged) = Arrangement()
                .withParticipantMapper()
                .withUserConfigRepositoryReturning(Either.Right(true))
                .withProtocol()
                .withEstablishedCall()
                .withShouldEndSFTOneOnOneCall(true)
                .arrange()

            onParticipantListChanged.onParticipantChanged(
                REMOTE_CONVERSATION_ID, data, null
            )
            advanceUntilIdle()
            yield()

            assertTrue { arrangement.isEndCallInvoked }
        }


    @Test
    fun givenMlsCallHelperReturnsFalse_whenParticipantListChangedCallBackHappens_thenDoNotEndCall() =
        testScope.runTest {
            val (arrangement, onParticipantListChanged) = Arrangement()
                .withParticipantMapper()
                .withUserConfigRepositoryReturning(Either.Right(true))
                .withProtocol()
                .withEstablishedCall()
                .withShouldEndSFTOneOnOneCall(false)
                .arrange()

            onParticipantListChanged.onParticipantChanged(
                REMOTE_CONVERSATION_ID, data, null
            )
            yield()

            assertFalse { arrangement.isEndCallInvoked }
        }


    internal class Arrangement {
        val callRepository = mock(CallRepository::class)
        val participantMapper = mock(ParticipantMapper::class)
        val userConfigRepository = mock(UserConfigRepository::class)
        val callHelper = mock(CallHelper::class)

        var isEndCallInvoked = false

        private val qualifiedIdMapper = QualifiedIdMapperImpl(TestUser.SELF.id)

        fun arrange() = this to OnParticipantListChanged(
            callRepository = callRepository,
            participantMapper = participantMapper,
            userConfigRepository = userConfigRepository,
            callHelper = callHelper,
            qualifiedIdMapper = qualifiedIdMapper,
            endCall = {
                isEndCallInvoked = true
            },
            callingScope = testScope,
        )

        fun withUserConfigRepositoryReturning(result: Either<StorageFailure, Boolean>) = apply {
            every {
                userConfigRepository.shouldUseSFTForOneOnOneCalls()
            }.returns(result)
        }

        fun withParticipantMapper() = apply {
            every {
                participantMapper.fromCallMemberToParticipantMinimized(any())
            }.returns(participant)
        }

        fun withProtocol() = apply {
            every {
                callRepository.currentCallProtocol(any())
            }.returns(mlsProtocolInfo)
        }

        suspend fun withEstablishedCall() = apply {
            coEvery {
                callRepository.establishedCallsFlow()
            }.returns(flowOf(listOf(call)))
        }

        fun withShouldEndSFTOneOnOneCall(result: Boolean) = apply {
            every {
                callHelper.shouldEndSFTOneOnOneCall(any(), any(), any(), any(), any())
            }.returns(result)
        }
    }

    companion object {
        lateinit var testScope: TestScope
        private const val REMOTE_CONVERSATION_ID = "c9mGRDNE7YRVRbk6jokwXNXPgU1n37iS"
        private val data = """
            {
              "convid": "c9mGRDNE7YRVRbk6jokwXNXPgU1n37iS",
              "members": [
                {
                  "userid": "userid1",
                  "clientid": "clientid1",
                  "aestab": "1",
                  "vrecv": "1",
                  "muted": "0"
                },
                {
                  "userid": "userid2",
                  "clientid": "clientid2",
                  "aestab": "1",
                  "vrecv": "1",
                  "muted": "0"
                }
              ]
            }
        """
        val participant = ParticipantMinimized(
            id = QualifiedID("userid1", "domain"),
            userId = QualifiedID("participantId", "participantDomain"),
            clientId = "abcd",
            isMuted = true,
            isCameraOn = false,
            isSharingScreen = false,
            hasEstablishedAudio = true
        )
        val mlsProtocolInfo = Conversation.ProtocolInfo.MLS(
            GroupID("groupid"),
            Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
            1UL,
            Clock.System.now(),
            CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        )
        private val conversationId = ConversationId("conversationId", "domainId")

        private val call = Call(
            conversationId = conversationId,
            status = CallStatus.ESTABLISHED,
            callerId = UserId("called-id", "domain"),
            isMuted = false,
            isCameraOn = false,
            isCbrEnabled = false,
            conversationName = null,
            conversationType = Conversation.Type.OneOnOne,
            callerName = null,
            callerTeamName = null,
            establishedTime = null
        )
    }
}
