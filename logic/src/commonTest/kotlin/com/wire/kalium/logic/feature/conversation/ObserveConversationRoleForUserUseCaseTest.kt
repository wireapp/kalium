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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationDetails.Group.Channel.ChannelAccess
import com.wire.kalium.logic.data.conversation.ConversationDetails.Group.Channel.ChannelAddPermission
import com.wire.kalium.logic.data.conversation.ConversationHistorySettings
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.data.user.type.UserTypeInfo
import com.wire.kalium.logic.feature.user.ObserveSelfUserUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObserveConversationRoleForUserUseCaseTest {

    @Test
    fun givenAnOrdinaryConversation_whenObservingRoles_thenMemberRolesAreProjected() = runTest(TestKaliumDispatcher.main) {
        val self = TestUser.SELF
        val other = TestUser.OTHER
        val conversationDetails = ConversationDetails.Group.Regular(
            conversation = TestConversation.GROUP(),
            isSelfUserMember = true,
            selfRole = Conversation.Member.Role.Admin,
        )
        val useCase = useCase(
            self = self,
            details = ObserveConversationDetailsUseCase.Result.Success(conversationDetails),
            members = listOf(
                MemberDetails(self, Conversation.Member.Role.Admin),
                MemberDetails(other, Conversation.Member.Role.Member),
            ),
        )

        val result = useCase(TestConversation.ID, other.id).first()

        assertEquals(TestConversation.GROUP().name, result.conversationName)
        assertEquals(Conversation.Member.Role.Member, result.userRole)
        assertEquals(Conversation.Member.Role.Admin, result.selfRole)
        assertEquals(TestConversation.ID, result.conversationId)
    }

    @Test
    fun givenASameTeamChannelAndTeamAdmin_whenObservingRoles_thenSelfRoleIsAdmin() = runTest(TestKaliumDispatcher.main) {
        val self = teamAdmin()
        val other = TestUser.OTHER
        val useCase = useCase(
            self = self,
            details = ObserveConversationDetailsUseCase.Result.Success(
                channelDetails(teamId = requireNotNull(self.teamId)),
            ),
            members = listOf(
                MemberDetails(self, Conversation.Member.Role.Member),
                MemberDetails(other, Conversation.Member.Role.Member),
            ),
        )

        val result = useCase(TestConversation.ID, other.id).first()

        assertEquals(Conversation.Member.Role.Admin, result.selfRole)
    }

    @Test
    fun givenACrossTeamChannelAndTeamAdmin_whenObservingRoles_thenMemberRoleIsKept() = runTest(TestKaliumDispatcher.main) {
        val self = teamAdmin()
        val other = TestUser.OTHER
        val useCase = useCase(
            self = self,
            details = ObserveConversationDetailsUseCase.Result.Success(
                channelDetails(teamId = TeamId("another-team")),
            ),
            members = listOf(
                MemberDetails(self, Conversation.Member.Role.Member),
                MemberDetails(other, Conversation.Member.Role.Member),
            ),
        )

        val result = useCase(TestConversation.ID, other.id).first()

        assertEquals(Conversation.Member.Role.Member, result.selfRole)
    }

    @Test
    fun givenConversationDetailsFail_whenObservingRoles_thenNoRoleIsEmitted() = runTest(TestKaliumDispatcher.main) {
        val self = TestUser.SELF
        val useCase = useCase(
            self = self,
            details = ObserveConversationDetailsUseCase.Result.Failure(StorageFailure.DataNotFound),
            members = listOf(MemberDetails(self, Conversation.Member.Role.Member)),
        )

        val results = useCase(TestConversation.ID, self.id).toList()

        assertTrue(results.isEmpty())
    }

    private suspend fun useCase(
        self: SelfUser,
        details: ObserveConversationDetailsUseCase.Result,
        members: List<MemberDetails>,
    ): ObserveConversationRoleForUserUseCase {
        val conversationRepository = mock<ConversationRepository>()
        val observeConversationMembers = mock<ObserveConversationMembersUseCase>()
        val observeSelfUser = mock<ObserveSelfUserUseCase>()
        everySuspend { conversationRepository.observeConversationDetailsById(any()) } returns flowOf(
            when (details) {
                is ObserveConversationDetailsUseCase.Result.Success -> Either.Right(details.conversationDetails)
                is ObserveConversationDetailsUseCase.Result.Failure -> Either.Left(details.storageFailure)
            },
        )
        everySuspend { observeConversationMembers(any()) } returns flowOf(members)
        everySuspend { observeSelfUser() } returns flowOf(self)
        return ObserveConversationRoleForUserUseCase(
            observeConversationMembers,
            ObserveConversationDetailsUseCase(conversationRepository, TestKaliumDispatcher),
            observeSelfUser,
        )
    }

    private fun teamAdmin(): SelfUser = TestUser.SELF.copy(
        userType = UserTypeInfo.Regular(UserType.ADMIN),
    )

    private fun channelDetails(teamId: TeamId): ConversationDetails.Group.Channel =
        ConversationDetails.Group.Channel(
            conversation = TestConversation.GROUP().copy(
                type = Conversation.Type.Group.Channel,
                teamId = teamId,
            ),
            isSelfUserMember = true,
            selfRole = Conversation.Member.Role.Member,
            access = ChannelAccess.PRIVATE,
            permission = ChannelAddPermission.ADMINS,
            historySharing = ConversationHistorySettings.Private,
        )
}
