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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.network.api.model.AdminlessConversationErrorResponse
import com.wire.kalium.network.api.model.QualifiedID as NetworkQualifiedID
import com.wire.kalium.network.exceptions.AdminlessConversationError
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RemoveMemberFromConversationUseCaseTest {

    @Test
    fun givenMemberAndConversation_WhenRemoveMemberIsSuccessful_ThenReturnSuccess() = runTest {
        val (arrangement, removeMemberUseCase) = Arrangement()
            .withRemoveMemberGroupIs(Either.Right(Unit))
            .arrange()

        val result = removeMemberUseCase(TestConversation.ID, TestConversation.USER_1)

        assertIs<RemoveMemberFromConversationUseCase.Result.Success>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationGroupRepository.deleteMember(eq(TestConversation.USER_1), eq(TestConversation.ID))
        }
    }

    @Test
    fun givenMemberAndConversation_WhenRemoveMemberFailed_ThenReturnFailure() = runTest {
        val (arrangement, removeMemberUseCase) = Arrangement()
            .withRemoveMemberGroupIs(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        val result = removeMemberUseCase(TestConversation.ID, TestConversation.USER_1)
        assertIs<RemoveMemberFromConversationUseCase.Result.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationGroupRepository.deleteMember(eq(TestConversation.USER_1), eq(TestConversation.ID))
        }
    }

    @Test
    fun givenAdminlessErrorWithEligibleMembers_WhenRemoveMemberFailed_ThenReturnAdminlessConversation() = runTest {
        val (arrangement, removeMemberUseCase) = Arrangement()
            .withRemoveMemberGroupIs(Either.Left(adminlessConversationFailure(listOf(TestUser.OTHER_USER_ID))))
            .arrange()

        val result = removeMemberUseCase(TestConversation.ID, TestConversation.USER_1)

        assertIs<RemoveMemberFromConversationUseCase.Result.Failure>(result)

        val eligibleMembers = (result.cause as AdminlessConversationFailure).eligibleMembers

        assertEquals(listOf(TestUser.OTHER_USER_ID), eligibleMembers)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationGroupRepository.deleteMember(eq(TestConversation.USER_1), eq(TestConversation.ID))
        }
    }

    @Test
    fun givenAdminlessErrorWithoutEligibleMembers_WhenRemoveMemberFailed_ThenReturnFailure() = runTest {
        val (arrangement, removeMemberUseCase) = Arrangement()
            .withRemoveMemberGroupIs(Either.Left(adminlessConversationFailure(emptyList())))
            .arrange()

        val result = removeMemberUseCase(TestConversation.ID, TestConversation.USER_1)

        assertIs<RemoveMemberFromConversationUseCase.Result.Failure>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationGroupRepository.deleteMember(eq(TestConversation.USER_1), eq(TestConversation.ID))
        }
    }

    private class Arrangement {

        val conversationGroupRepository = mock<ConversationGroupRepository>(mode = MockMode.autoUnit)

        private val removeMemberUseCase = RemoveMemberFromConversationUseCaseImpl(
            conversationGroupRepository
        )

        suspend fun withRemoveMemberGroupIs(either: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                conversationGroupRepository.deleteMember(any(), any())
            } returns either
        }

        fun arrange() = this to removeMemberUseCase
    }

    private companion object {
        fun adminlessConversationFailure(eligibleMembers: List<com.wire.kalium.logic.data.user.UserId>) =
            NetworkFailure.ServerMiscommunication(
                AdminlessConversationError(
                    AdminlessConversationErrorResponse(
                        code = 400,
                        message = "Adminless conversation",
                        label = AdminlessConversationErrorResponse.LABEL,
                        eligibleMembers = eligibleMembers.map { NetworkQualifiedID(it.value, it.domain) },
                    )
                )
            )
    }
}
