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
package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallMetadata
import com.wire.kalium.logic.data.call.CallMetadataProfile
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.call.ParticipantMinimized
import com.wire.kalium.logic.data.call.RecentlyEndedCallMetadata
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.conversation.ObserveConversationMembersUseCase
import com.wire.kalium.logic.framework.TestCall.CALLER_ID
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.framework.TestUser.OTHER_MINIMIZED
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class CreateAndPersistRecentlyEndedCallMetadataUseCaseTest {

    @Test
    fun givenCallAndEndCallReaction_whenUseCaseInvoked_thenRecentlyCallMetadataIsProperlyUpdated() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withOutgoingCall()
            .withSelfTeamIdPresent()
            .withConversationMembers()
            .arrange()

        // when
        useCase(
            conversationId = CONVERSATION_ID,
            callEndedReason = 2
        )

        // then
        coVerify {
            arrangement.callRepository.updateRecentlyEndedCallMetadata(DEFAULT_ENDED_CALL_METADATA)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenCallDetailsWithinConversationWithGuests_whenUseCaseInvoked_thenRecentlyEndedCallMetadataHasProperGuestsCount() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withOutgoingCall()
            .withSelfTeamIdPresent()
            .withConversationGuests()
            .arrange()

        // when
        useCase(
            conversationId = CONVERSATION_ID,
            callEndedReason = 2
        )

        // then
        coVerify {
            arrangement.callRepository.updateRecentlyEndedCallMetadata(
                DEFAULT_ENDED_CALL_METADATA.copy(
                    conversationDetails = DEFAULT_ENDED_CALL_METADATA.conversationDetails.copy(
                        conversationGuests = 1
                    )
                )
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenCallDetailsWithinConversationWithGuests_whenUseCaseInvoked_thenRecentlyEndedCallMetadataHasProperGuestsProCount() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withOutgoingCall()
            .withSelfTeamIdPresent()
            .withConversationGuestsPro()
            .arrange()

        // when
        useCase(
            conversationId = CONVERSATION_ID,
            callEndedReason = 2
        )

        // then
        coVerify {
            arrangement.callRepository.updateRecentlyEndedCallMetadata(
                DEFAULT_ENDED_CALL_METADATA.copy(
                    conversationDetails = DEFAULT_ENDED_CALL_METADATA.conversationDetails.copy(
                        conversationGuests = 1,
                        conversationGuestsPro = 1
                    )
                )
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenIncomingCallDetails_whenUseCaseInvoked_thenReturnCorrectMetadataIncomingCall() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withIncomingCall()
            .withSelfTeamIdPresent()
            .withConversationMembers()
            .arrange()

        // when
        useCase(
            conversationId = CONVERSATION_ID,
            callEndedReason = 2
        )

        // then
        coVerify {
            arrangement.callRepository.updateRecentlyEndedCallMetadata(
                DEFAULT_ENDED_CALL_METADATA.copy(
                    callDetails = DEFAULT_ENDED_CALL_METADATA.callDetails.copy(
                        isOutgoingCall = false
                    )
                )
            )
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val observeConversationMembers = mock(ObserveConversationMembersUseCase::class)

        @Mock
        val selfTeamIdProvider = mock(SelfTeamIdProvider::class)

        @Mock
        val callRepository = mock(CallRepository::class)

        fun withOutgoingCall() = apply {
            every { callRepository.getCallMetadataProfile() }
                .returns(
                    CallMetadataProfile(
                        mapOf(
                            CONVERSATION_ID to callMetadata().copy(
                                callStatus = CallStatus.STARTED
                            )
                        )
                    )
                )
        }

        fun withIncomingCall() = apply {
            every { callRepository.getCallMetadataProfile() }
                .returns(
                    CallMetadataProfile(
                        mapOf(
                            CONVERSATION_ID to callMetadata().copy(
                                callerId = CALLER_ID.copy(value = "external"),
                                callStatus = CallStatus.INCOMING
                            )
                        )
                    )
                )
        }

        suspend fun withConversationMembers() = apply {
            coEvery { observeConversationMembers(any()) }.returns(
                flowOf(
                    listOf(
                        MemberDetails(TestUser.SELF, Conversation.Member.Role.Admin),
                        MemberDetails(TestUser.OTHER, Conversation.Member.Role.Member)
                    )
                )
            )
        }

        suspend fun withConversationGuests() = apply {
            coEvery { observeConversationMembers(any()) }.returns(
                flowOf(
                    listOf(
                        MemberDetails(TestUser.SELF, Conversation.Member.Role.Admin),
                        MemberDetails(TestUser.OTHER.copy(userType = UserType.GUEST, teamId = null), Conversation.Member.Role.Member)
                    )
                )
            )
        }

        suspend fun withConversationGuestsPro() = apply {
            coEvery { observeConversationMembers(any()) }.returns(
                flowOf(
                    listOf(
                        MemberDetails(TestUser.SELF, Conversation.Member.Role.Admin),
                        MemberDetails(TestUser.OTHER.copy(userType = UserType.GUEST), Conversation.Member.Role.Member)
                    )
                )
            )
        }

        suspend fun withSelfTeamIdPresent() = apply {
            coEvery { selfTeamIdProvider() }.returns(Either.Right(TestUser.SELF.teamId))
        }

        fun arrange(): Pair<Arrangement, CreateAndPersistRecentlyEndedCallMetadataUseCase> =
            this to CreateAndPersistRecentlyEndedCallMetadataUseCaseImpl(
                callRepository = callRepository,
                observeConversationMembers = observeConversationMembers,
                selfTeamIdProvider = selfTeamIdProvider
            )

        private fun callMetadata(): CallMetadata {
            return CallMetadata(
                callerId = CALLER_ID.copy(value = "ownerId"),
                isMuted = true,
                isCameraOn = false,
                isCbrEnabled = false,
                conversationName = null,
                users = listOf(
                    OTHER_MINIMIZED.copy(id = CALLER_ID.copy(value = "ownerId"), userType = UserType.OWNER),
                    OTHER_MINIMIZED
                ),
                participants = listOf(
                    ParticipantMinimized(
                        id = CALLER_ID.copy(value = "ownerId"),
                        userId = CALLER_ID.copy(value = "ownerId"),
                        clientId = "abcd",
                        isMuted = true,
                        isCameraOn = false,
                        isSharingScreen = false,
                        hasEstablishedAudio = true
                    ),
                    ParticipantMinimized(
                        id = CALLER_ID,
                        userId = CALLER_ID,
                        clientId = "abcd",
                        isMuted = true,
                        isCameraOn = false,
                        isSharingScreen = false,
                        hasEstablishedAudio = true
                    )
                ),
                conversationType = Conversation.Type.OneOnOne,
                callerName = "User Name",
                callerTeamName = null,
                callStatus = CallStatus.ESTABLISHED,
                protocol = Conversation.ProtocolInfo.Proteus,
                activeSpeakers = mapOf()
            )
        }
    }

    private companion object {
        val CONVERSATION_ID = ConversationId(value = "value", domain = "domain")
        val DEFAULT_ENDED_CALL_METADATA = RecentlyEndedCallMetadata(
            callEndReason = 2,
            isTeamMember = true,
            callDetails = RecentlyEndedCallMetadata.CallDetails(
                isCallScreenShare = false,
                screenShareDurationInSeconds = 0L,
                callScreenShareUniques = 0,
                isOutgoingCall = true,
                callDurationInSeconds = 0L,
                callParticipantsCount = 2,
                conversationServices = 0,
                callAVSwitchToggle = false,
                callVideoEnabled = false
            ),
            conversationDetails = RecentlyEndedCallMetadata.ConversationDetails(
                conversationType = Conversation.Type.OneOnOne,
                conversationSize = 2,
                conversationGuests = 0,
                conversationGuestsPro = 0
            )
        )
    }
}
