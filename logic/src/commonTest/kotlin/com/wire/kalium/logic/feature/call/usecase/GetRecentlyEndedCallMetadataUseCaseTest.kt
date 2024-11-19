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

import com.wire.kalium.logic.data.call.RecentlyEndedCallMetadata
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.conversation.ObserveConversationMembersUseCase
import com.wire.kalium.logic.feature.user.GetSelfUserUseCase
import com.wire.kalium.logic.framework.TestCall
import com.wire.kalium.logic.framework.TestCall.CALLER_ID
import com.wire.kalium.logic.framework.TestUser
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetRecentlyEndedCallMetadataUseCaseTest {

    @Test
    fun givenCallAndEndCallReaction_whenUseCaseInvoked_thenCreatesProperMetadata() = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withSelfUser()
            .withConversationMembers()
            .arrange()

        // when
        val metadata = useCase(TestCall.oneOnOneEstablishedCall(), callEndedReason = 2)

        // then
        assertEquals(2, metadata.callEndReason)
        assertEquals(
            RecentlyEndedCallMetadata.CallDetails(
                isCallScreenShare = false,
                callScreenShareUniques = 0,
                isOutgoingCall = false,
                callDurationInSeconds = 0L,
                callParticipantsCount = 1,
                conversationServices = 0,
                callAVSwitchToggle = false,
                callVideoEnabled = false
            ), metadata.callDetails
        )
        assertEquals(
            RecentlyEndedCallMetadata.ConversationDetails(
                conversationType = Conversation.Type.ONE_ON_ONE,
                conversationSize = 2,
                conversationGuests = 0,
                conversationGuestsPro = 0
            ), metadata.conversationDetails
        )
        assertEquals(true, metadata.isTeamMember)
    }

    @Test
    fun givenCallDetailsWithinConversationWithGuests_whenUseCaseInvoked_thenReturnCorrectMetadataGuestsCount() = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withSelfUser()
            .withConversationGuests()
            .arrange()

        // when
        val metadata = useCase(TestCall.oneOnOneEstablishedCall(), callEndedReason = 2)

        // then
        assertEquals(1, metadata.conversationDetails.conversationGuests)
    }

    @Test
    fun givenIncomingCallDetails_whenUseCaseInvoked_thenReturnCorrectMetadataIncomingCall() = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withSelfUser()
            .withConversationGuests()
            .arrange()

        // when
        val metadata = useCase(TestCall.oneOnOneEstablishedCall().copy(callerId = CALLER_ID.copy(value = "external")), callEndedReason = 2)

        // then
        assertEquals(false, metadata.callDetails.isOutgoingCall)
    }

    private class Arrangement {
        @Mock
        val observeConversationMembers = mock(ObserveConversationMembersUseCase::class)

        @Mock
        val getSelf = mock(GetSelfUserUseCase::class)

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
                        MemberDetails(TestUser.OTHER.copy(userType = UserType.GUEST), Conversation.Member.Role.Member)
                    )
                )
            )
        }

        suspend fun withSelfUser() = apply {
            coEvery { getSelf() }.returns(flowOf(TestUser.SELF))
        }

        fun arrange(): Pair<Arrangement, GetRecentlyEndedCallMetadataUseCase> = this to GetRecentlyEndedCallMetadataUseCaseImpl(
            observeConversationMembers = observeConversationMembers,
            getSelf = getSelf
        )
    }
}
