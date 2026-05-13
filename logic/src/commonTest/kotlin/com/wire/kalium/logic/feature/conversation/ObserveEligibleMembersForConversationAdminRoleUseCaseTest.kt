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
import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.data.user.type.UserTypeInfo
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObserveEligibleMembersForConversationAdminRoleUseCaseTest {

    @Test
    fun givenSelfUser_whenObserving_thenFilteredOut() = runTest {
        val selfMember = MemberDetails(TestUser.SELF, Conversation.Member.Role.Admin)
        val (_, useCase) = Arrangement()
            .withMembers(listOf(selfMember))
            .arrange()

        val result = useCase(TestConversation.ID).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun givenFederatedMember_whenObserving_thenFilteredOut() = runTest {
        val federatedUser = TestUser.OTHER.copy(userType = UserTypeInfo.Regular(UserType.FEDERATED))
        val member = MemberDetails(federatedUser, Conversation.Member.Role.Member)
        val (_, useCase) = Arrangement()
            .withMembers(listOf(member))
            .arrange()

        val result = useCase(TestConversation.ID).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun givenBotMember_whenObserving_thenFilteredOut() = runTest {
        val botUser = TestUser.OTHER.copy(userType = UserTypeInfo.Bot)
        val member = MemberDetails(botUser, Conversation.Member.Role.Member)
        val (_, useCase) = Arrangement()
            .withMembers(listOf(member))
            .arrange()

        val result = useCase(TestConversation.ID).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun givenMemberWithNullName_whenObserving_thenFilteredOut() = runTest {
        val userWithNullName = TestUser.OTHER.copy(name = null)
        val member = MemberDetails(userWithNullName, Conversation.Member.Role.Member)
        val (_, useCase) = Arrangement()
            .withMembers(listOf(member))
            .arrange()

        val result = useCase(TestConversation.ID).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun givenMemberWithEmptyName_whenObserving_thenFilteredOut() = runTest {
        val userWithEmptyName = TestUser.OTHER.copy(name = "")
        val member = MemberDetails(userWithEmptyName, Conversation.Member.Role.Member)
        val (_, useCase) = Arrangement()
            .withMembers(listOf(member))
            .arrange()

        val result = useCase(TestConversation.ID).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun givenMemberWithNullHandle_whenObserving_thenFilteredOut() = runTest {
        val userWithNullHandle = TestUser.OTHER.copy(handle = null)
        val member = MemberDetails(userWithNullHandle, Conversation.Member.Role.Member)
        val (_, useCase) = Arrangement()
            .withMembers(listOf(member))
            .arrange()

        val result = useCase(TestConversation.ID).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun givenMemberWithEmptyHandle_whenObserving_thenFilteredOut() = runTest {
        val userWithEmptyHandle = TestUser.OTHER.copy(handle = "")
        val member = MemberDetails(userWithEmptyHandle, Conversation.Member.Role.Member)
        val (_, useCase) = Arrangement()
            .withMembers(listOf(member))
            .arrange()

        val result = useCase(TestConversation.ID).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun givenTemporaryMember_whenObserving_thenFilteredOut() = runTest {
        val temporaryUser = TestUser.OTHER.copy(expiresAt = Instant.fromEpochMilliseconds(9999999999L))
        val member = MemberDetails(temporaryUser, Conversation.Member.Role.Member)
        val (_, useCase) = Arrangement()
            .withMembers(listOf(member))
            .arrange()

        val result = useCase(TestConversation.ID).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun givenEligibleMember_whenObserving_thenIncluded() = runTest {
        val eligibleUser = TestUser.OTHER.copy(
            userType = UserTypeInfo.Regular(UserType.INTERNAL),
            name = "Alice",
            handle = "alice",
            expiresAt = null,
        )
        val member = MemberDetails(eligibleUser, Conversation.Member.Role.Member)
        val (_, useCase) = Arrangement()
            .withMembers(listOf(member))
            .arrange()

        val result = useCase(TestConversation.ID).first()

        assertEquals(1, result.size)
        assertEquals(member, result.first())
    }

    @Test
    fun givenMixedMembers_whenObserving_thenOnlyEligibleIncluded() = runTest {
        val eligibleUser = TestUser.OTHER.copy(
            userType = UserTypeInfo.Regular(UserType.INTERNAL),
            name = "Alice",
            handle = "alice",
            expiresAt = null,
        )
        val federatedUser = TestUser.OTHER.copy(
            id = TestUser.OTHER_USER_ID_2,
            userType = UserTypeInfo.Regular(UserType.FEDERATED),
        )
        val selfMember = MemberDetails(TestUser.SELF, Conversation.Member.Role.Admin)
        val eligibleMember = MemberDetails(eligibleUser, Conversation.Member.Role.Member)
        val ineligibleMember = MemberDetails(federatedUser, Conversation.Member.Role.Member)
        val (_, useCase) = Arrangement()
            .withMembers(listOf(selfMember, eligibleMember, ineligibleMember))
            .arrange()

        val result = useCase(TestConversation.ID).first()

        assertEquals(1, result.size)
        assertEquals(eligibleMember, result.first())
    }

    private class Arrangement {
        val observeConversationMembers = mock<ObserveConversationMembersUseCase>(mode = MockMode.autoUnit)

        fun withMembers(members: List<MemberDetails>) = apply {
            everySuspend { observeConversationMembers(any()) } returns flowOf(members)
        }

        fun arrange() = this to ObserveEligibleMembersForConversationAdminRoleUseCaseImpl(
            observeConversationMembers,
            TestUser.USER_ID,
        )
    }
}
