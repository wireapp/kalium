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
package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MLSCallHelperTest {

    @Test
    fun givenMlsProtocol_whenShouldEndSFTOneOnOneCallIsCalled_thenReturnCorrectValue() =
        runTest {
            val (_, mLSCallHelper) = Arrangement()
                .withShouldUseSFTForOneOnOneCallsReturning(Either.Right(true))
                .arrange()

            // one participant in the call
            val shouldEndSFTOneOnOneCall1 = mLSCallHelper.shouldEndSFTOneOnOneCall(
                conversationId = conversationId,
                callProtocol = CONVERSATION_MLS_PROTOCOL_INFO,
                conversationType = Conversation.Type.ONE_ON_ONE,
                newCallParticipants = listOf(participant1),
                previousCallParticipants = listOf(participant1)
            )
            assertFalse { shouldEndSFTOneOnOneCall1 }

            // Audio not lost for the second participant
            val shouldEndSFTOneOnOneCall2 = mLSCallHelper.shouldEndSFTOneOnOneCall(
                conversationId = conversationId,
                callProtocol = CONVERSATION_MLS_PROTOCOL_INFO,
                conversationType = Conversation.Type.GROUP,
                newCallParticipants = listOf(participant1, participant2),
                previousCallParticipants = listOf(participant1, participant2)
            )
            assertFalse { shouldEndSFTOneOnOneCall2 }

            // Audio lost for the second participant
            val shouldEndSFTOneOnOneCall3 = mLSCallHelper.shouldEndSFTOneOnOneCall(
                conversationId = conversationId,
                callProtocol = CONVERSATION_MLS_PROTOCOL_INFO,
                conversationType = Conversation.Type.ONE_ON_ONE,
                previousCallParticipants = listOf(participant1, participant2),
                newCallParticipants = listOf(
                    participant1,
                    participant2.copy(hasEstablishedAudio = false)
                )
            )
            assertTrue { shouldEndSFTOneOnOneCall3 }
        }

    @Test
    fun givenProteusProtocol_whenShouldEndSFTOneOnOneCallIsCalled_thenReturnCorrectValue() =
        runTest {

            val (_, mLSCallHelper) = Arrangement()
                .withShouldUseSFTForOneOnOneCallsReturning(Either.Right(true))
                .arrange()

            // participants list has 2 items for the new list and the previous list
            val shouldEndSFTOneOnOneCall1 = mLSCallHelper.shouldEndSFTOneOnOneCall(
                conversationId = conversationId,
                callProtocol = Conversation.ProtocolInfo.Proteus,
                conversationType = Conversation.Type.ONE_ON_ONE,
                newCallParticipants = listOf(participant1, participant2),
                previousCallParticipants = listOf(participant1, participant2)
            )
            assertFalse { shouldEndSFTOneOnOneCall1 }

            // new participants list has 1 participant
            val shouldEndSFTOneOnOneCall2 = mLSCallHelper.shouldEndSFTOneOnOneCall(
                conversationId = conversationId,
                callProtocol = Conversation.ProtocolInfo.Proteus,
                conversationType = Conversation.Type.ONE_ON_ONE,
                newCallParticipants = listOf(participant1),
                previousCallParticipants = listOf(participant1, participant2)
            )
            assertTrue { shouldEndSFTOneOnOneCall2 }
        }

    private class Arrangement {

        @Mock
        val callRepository = mock(classOf<CallRepository>())

        @Mock
        val subconversationRepository = mock(classOf<SubconversationRepository>())

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        private val mLSCallHelper: CallHelper = CallHelperImpl(
            callRepository = callRepository,
            subconversationRepository = subconversationRepository,
            userConfigRepository = userConfigRepository
        )

        fun arrange() = this to mLSCallHelper

        fun withShouldUseSFTForOneOnOneCallsReturning(result: Either<StorageFailure, Boolean>) =
            apply {
                given(userConfigRepository)
                    .function(userConfigRepository::shouldUseSFTForOneOnOneCalls)
                    .whenInvoked()
                    .thenReturn(result)
            }
    }

    companion object {
        val conversationId = ConversationId(value = "convId", domain = "domainId")
        val CONVERSATION_MLS_PROTOCOL_INFO = Conversation.ProtocolInfo.MLS(
            GroupID("GROUP_ID"),
            Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
            5UL,
            Instant.parse("2021-03-30T15:36:00.000Z"),
            cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        )
        val participant1 = Participant(
            id = QualifiedID("participantId", "participantDomain"),
            clientId = "abcd",
            name = "name",
            isMuted = true,
            isSpeaking = false,
            isCameraOn = false,
            avatarAssetId = null,
            isSharingScreen = false,
            hasEstablishedAudio = true
        )
        val participant2 = participant1.copy(
            id = QualifiedID("participantId2", "participantDomain2"),
            clientId = "efgh"
        )
    }
}
