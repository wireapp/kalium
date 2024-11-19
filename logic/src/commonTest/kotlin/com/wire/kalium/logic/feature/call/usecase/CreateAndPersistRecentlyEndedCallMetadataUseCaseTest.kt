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

import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.Participant
import com.wire.kalium.logic.data.call.RecentlyEndedCallMetadata
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.conversation.ObserveConversationMembersUseCase
import com.wire.kalium.logic.feature.user.GetSelfUserUseCase
import com.wire.kalium.logic.framework.TestCall
import com.wire.kalium.logic.framework.TestCall.CALLER_ID
import com.wire.kalium.logic.framework.TestUser
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
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
            .withSelfUser()
            .withConversationMembers()
            .arrange()

        // when
        useCase(
            conversationId = ConversationId(value = "value", domain = "domain"),
            callEndedReason = 2
        )

        // then
        coVerify {
            arrangement.callRepository.updateRecentlyEndedCall(DEFAULT_ENDED_CALL_METADATA)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenCallDetailsWithinConversationWithGuests_whenUseCaseInvoked_thenRecentlyEndedCallMetadataHasProperGuestsCount() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withOutgoingCall()
            .withSelfUser()
            .withConversationGuests()
            .arrange()

        // when
        useCase(
            conversationId = ConversationId(value = "value", domain = "domain"),
            callEndedReason = 2
        )

        // then
        coVerify {
            arrangement.callRepository.updateRecentlyEndedCall(
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
            .withSelfUser()
            .withConversationGuestsPro()
            .arrange()

        // when
        useCase(
            conversationId = ConversationId(value = "value", domain = "domain"),
            callEndedReason = 2
        )

        // then
        coVerify {
            arrangement.callRepository.updateRecentlyEndedCall(
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
            .withSelfUser()
            .withConversationMembers()
            .arrange()

        // when
        useCase(
            conversationId = ConversationId(value = "value", domain = "domain"),
            callEndedReason = 2
        )

        // then
        coVerify {
            arrangement.callRepository.updateRecentlyEndedCall(
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
        val getSelf = mock(GetSelfUserUseCase::class)

        @Mock
        val callRepository = mock(CallRepository::class)

        suspend fun withOutgoingCall() = apply {
            coEvery { callRepository.observeCurrentCall(any()) }
                .returns(flowOf(callWithOwner()))
        }

        suspend fun withIncomingCall() = apply {
            coEvery { callRepository.observeCurrentCall(any()) }
                .returns(flowOf(callWithOwner().copy(callerId = CALLER_ID.copy(value = "external"))))
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

        suspend fun withSelfUser() = apply {
            coEvery { getSelf() }.returns(flowOf(TestUser.SELF))
        }

        fun arrange(): Pair<Arrangement, CreateAndPersistRecentlyEndedCallMetadataUseCase> =
            this to CreateAndPersistRecentlyEndedCallMetadataUseCaseImpl(
                callRepository = callRepository,
                observeConversationMembers = observeConversationMembers,
                getSelf = getSelf
            )

        private fun callWithOwner(): Call {
            return TestCall.oneOnOneEstablishedCall()
                .copy(
                    callerId = CALLER_ID.copy(value = "ownerId"),
                    participants = TestCall.oneOnOneEstablishedCall().participants.plus(
                        Participant(
                            id = CALLER_ID.copy(value = "ownerId"),
                            clientId = "abcd",
                            isMuted = true,
                            isCameraOn = false,
                            isSharingScreen = false,
                            hasEstablishedAudio = true,
                            name = "User Name",
                            avatarAssetId = null,
                            userType = UserType.OWNER,
                            isSpeaking = false,
                            accentId = 0
                        )
                    )
                )
        }
    }

    private companion object {
        val DEFAULT_ENDED_CALL_METADATA = RecentlyEndedCallMetadata(
            callEndReason = 2,
            isTeamMember = true,
            callDetails = RecentlyEndedCallMetadata.CallDetails(
                isCallScreenShare = false,
                callScreenShareUniques = 0,
                isOutgoingCall = true,
                callDurationInSeconds = 0L,
                callParticipantsCount = 2,
                conversationServices = 0,
                callAVSwitchToggle = false,
                callVideoEnabled = false
            ),
            conversationDetails = RecentlyEndedCallMetadata.ConversationDetails(
                conversationType = Conversation.Type.ONE_ON_ONE,
                conversationSize = 2,
                conversationGuests = 0,
                conversationGuestsPro = 0
            )
        )
    }
}
