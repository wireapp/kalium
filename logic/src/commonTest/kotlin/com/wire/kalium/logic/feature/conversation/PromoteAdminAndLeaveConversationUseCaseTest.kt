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
import com.wire.kalium.logic.feature.conversation.RemoveMemberFromConversationUseCase.Result.Failure
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PromoteAdminAndLeaveConversationUseCaseTest {

    @Test
    fun givenPromoteSucceedsAndLeaveSucceeds_whenInvoked_thenReturnSuccess() = runTest {
        val (_, useCase) = Arrangement()
            .withPromoteResult(UpdateConversationMemberRoleResult.Success)
            .withLeaveResult(RemoveMemberFromConversationUseCase.Result.Success)
            .arrange()

        val result = useCase(TestConversation.ID, TestUser.OTHER_USER_ID)

        assertIs<PromoteAdminAndLeaveConversationUseCase.Result.Success>(result)
    }

    @Test
    fun givenPromoteFails_whenInvoked_thenReturnFailedToPromoteUserAndLeaveNotCalled() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withPromoteResult(UpdateConversationMemberRoleResult.Failure)
            .arrange()

        val result = useCase(TestConversation.ID, TestUser.OTHER_USER_ID)

        assertIs<PromoteAdminAndLeaveConversationUseCase.Result.FailedToPromoteUser>(result)
        verifySuspend(VerifyMode.not) { arrangement.leaveConversation(any()) }
    }

    @Test
    fun givenPromoteSucceedsAndLeaveFails_whenInvoked_thenReturnFailedToLeaveConversation() = runTest {
        val (_, useCase) = Arrangement()
            .withPromoteResult(UpdateConversationMemberRoleResult.Success)
            .withLeaveResult(Failure(StorageFailure.DataNotFound))
            .arrange()

        val result = useCase(TestConversation.ID, TestUser.OTHER_USER_ID)

        assertIs<PromoteAdminAndLeaveConversationUseCase.Result.FailedToLeaveConversation>(result)
    }

    @Test
    fun givenPromoteSucceedsAndLeaveFailsWithAdminlessError_whenInvoked_thenReturnEligibleMembers() = runTest {
        val (_, useCase) = Arrangement()
            .withPromoteResult(UpdateConversationMemberRoleResult.Success)
            .withLeaveResult(Failure(AdminlessConversationFailure(listOf(TestUser.OTHER_USER_ID))))
            .arrange()

        val result = useCase(TestConversation.ID, TestUser.OTHER_USER_ID)

        assertIs<PromoteAdminAndLeaveConversationUseCase.Result.FailedToLeaveConversation>(result)
        assertEquals(TestUser.OTHER_USER_ID, result.eligibleMembers.first())
    }

    @Test
    fun givenPromoteSucceedsAndLeaveFailsWithAdminlessErrorWithoutMembers_whenInvoked_thenReturnFailedToLeaveConversation() = runTest {
        val (_, useCase) = Arrangement()
            .withPromoteResult(UpdateConversationMemberRoleResult.Success)
            .withLeaveResult(Failure(AdminlessConversationFailure(emptyList())))
            .arrange()

        val result = useCase(TestConversation.ID, TestUser.OTHER_USER_ID)

        assertIs<PromoteAdminAndLeaveConversationUseCase.Result.FailedToLeaveConversation>(result)
    }

    private class Arrangement {
        val updateConversationMemberRole: UpdateConversationMemberRoleUseCase = mock()
        val leaveConversation: LeaveConversationUseCase = mock()

        init {
            everySuspend { updateConversationMemberRole(any(), any(), any()) } returns UpdateConversationMemberRoleResult.Success
            everySuspend { leaveConversation(any()) } returns RemoveMemberFromConversationUseCase.Result.Success
        }

        fun withPromoteResult(result: UpdateConversationMemberRoleResult) = apply {
            everySuspend { updateConversationMemberRole(any(), any(), any()) } returns result
        }

        fun withLeaveResult(result: RemoveMemberFromConversationUseCase.Result) = apply {
            everySuspend { leaveConversation(any()) } returns result
        }

        fun arrange() = this to PromoteAdminAndLeaveConversationUseCaseImpl(updateConversationMemberRole, leaveConversation)
    }
}
