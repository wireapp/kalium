/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CheckConversationLeaveConditionsUseCaseTest {

    @Test
    fun givenSelfIsRegularMember_whenCheckingLeaveConditions_thenReturnAllow() = runTest {
        val members = listOf(
            Conversation.Member(TestUser.USER_ID, Conversation.Member.Role.Member),
        )
        val (_, useCase) = Arrangement()
            .withMembers(members)
            .arrange()

        val result = useCase(TestConversation.ID)

        assertIs<CheckConversationLeaveConditionsUseCase.Result.Allow>(result)
    }

    @Test
    fun givenSelfIsNotInConversation_whenCheckingLeaveConditions_thenReturnAllow() = runTest {
        val members = listOf(
            Conversation.Member(TestUser.OTHER_USER_ID, Conversation.Member.Role.Admin),
        )
        val (_, useCase) = Arrangement()
            .withMembers(members)
            .arrange()

        val result = useCase(TestConversation.ID)

        assertIs<CheckConversationLeaveConditionsUseCase.Result.Allow>(result)
    }

    @Test
    fun givenSelfIsAdminAndAnotherAdminExists_whenCheckingLeaveConditions_thenReturnAllow() = runTest {
        val members = listOf(
            Conversation.Member(TestUser.USER_ID, Conversation.Member.Role.Admin),
            Conversation.Member(TestUser.OTHER_USER_ID, Conversation.Member.Role.Admin),
        )
        val (_, useCase) = Arrangement()
            .withMembers(members)
            .arrange()

        val result = useCase(TestConversation.ID)

        assertIs<CheckConversationLeaveConditionsUseCase.Result.Allow>(result)
    }

    @Test
    fun givenSelfIsSoleAdminWithNoOtherMembers_whenCheckingLeaveConditions_thenReturnAllow() = runTest {
        val members = listOf(
            Conversation.Member(TestUser.USER_ID, Conversation.Member.Role.Admin),
        )
        val (_, useCase) = Arrangement()
            .withMembers(members)
            .arrange()

        val result = useCase(TestConversation.ID)

        assertIs<CheckConversationLeaveConditionsUseCase.Result.Allow>(result)
    }

    @Test
    fun givenSelfIsSoleAdminAndEligibleUsersExist_whenCheckingLeaveConditions_thenReturnDoNotAllowWithEligibleUsers() = runTest {
        val eligibleMember = MemberDetails(TestUser.OTHER, Conversation.Member.Role.Member)
        val members = listOf(
            Conversation.Member(TestUser.USER_ID, Conversation.Member.Role.Admin),
            Conversation.Member(TestUser.OTHER_USER_ID, Conversation.Member.Role.Member),
        )
        val (_, useCase) = Arrangement()
            .withMembers(members)
            .withEligibleMembers(listOf(eligibleMember))
            .arrange()

        val result = useCase(TestConversation.ID)

        assertIs<CheckConversationLeaveConditionsUseCase.Result.DoNotAllow>(result)
        assertEquals(true, result.eligibleUsersAvailable)
    }

    @Test
    fun givenSelfIsSoleAdminAndNoEligibleUsers_whenCheckingLeaveConditions_thenReturnDoNotAllowWithNoEligibleUsers() = runTest {
        val members = listOf(
            Conversation.Member(TestUser.USER_ID, Conversation.Member.Role.Admin),
            Conversation.Member(TestUser.OTHER_USER_ID, Conversation.Member.Role.Member),
        )
        val (_, useCase) = Arrangement()
            .withMembers(members)
            .withEligibleMembers(emptyList())
            .arrange()

        val result = useCase(TestConversation.ID)

        assertIs<CheckConversationLeaveConditionsUseCase.Result.DoNotAllow>(result)
        assertEquals(false, result.eligibleUsersAvailable)
    }

    @Test
    fun givenEmptyMembers_whenCheckingLeaveConditions_thenReturnError() = runTest {
        val (_, useCase) = Arrangement()
            .withMembers(emptyList())
            .arrange()

        val result = useCase(TestConversation.ID)

        assertIs<CheckConversationLeaveConditionsUseCase.Result.Error>(result)
    }

    private class Arrangement {
        val conversationRepository = mock<ConversationRepository>()
        val observeEligibleMembers = mock<ObserveEligibleMembersForConversationAdminRoleUseCase>()

        fun withMembers(members: List<Conversation.Member>) = apply {
            everySuspend {
                conversationRepository.observeConversationMembers(any())
            } returns flowOf(members)
        }

        fun withEligibleMembers(members: List<MemberDetails>) = apply {
            everySuspend {
                observeEligibleMembers(any())
            } returns flowOf(members)
        }

        fun arrange() = this to CheckConversationLeaveConditionsUseCaseImpl(
            conversationRepository,
            observeEligibleMembers,
            TestUser.USER_ID,
        )
    }
}
