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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class AddMemberToConversationUseCaseTest {

    @Test
    fun givenMemberAndConversation_WhenAddMemberIsSuccessful_ThenReturnSuccess() = runTest {
        val (arrangement, addMemberUseCase) = Arrangement()
            .withAddMembers(Either.Right(Unit))
            .withInsertOrIgnoreIncompleteUsers()
            .arrange()

        val result = addMemberUseCase(TestConversation.ID, listOf(TestConversation.USER_1))

        assertIs<AddMemberToConversationUseCase.Result.Success>(result)

        coVerify {
            arrangement.conversationGroupRepository.addMembers(eq(listOf(TestConversation.USER_1)), eq(TestConversation.ID))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.userRepository.insertOrIgnoreIncompleteUsers(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.refreshUsersWithoutMetadata.invoke()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberAndConversation_WhenAddMemberFailed_ThenReturnFailure() = runTest {
        val (arrangement, addMemberUseCase) = Arrangement()
            .withAddMembers(Either.Left(StorageFailure.DataNotFound))
            .withInsertOrIgnoreIncompleteUsers()
            .arrange()

        val result = addMemberUseCase(TestConversation.ID, listOf(TestConversation.USER_1))
        assertIs<AddMemberToConversationUseCase.Result.Failure>(result)

        coVerify {
            arrangement.conversationGroupRepository.addMembers(eq(listOf(TestConversation.USER_1)), eq(TestConversation.ID))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.userRepository.insertOrIgnoreIncompleteUsers(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.refreshUsersWithoutMetadata.invoke()
        }.wasInvoked(exactly = once)
    }

    private class Arrangement : UserRepositoryArrangement by UserRepositoryArrangementImpl() {

        val conversationGroupRepository = mock(ConversationGroupRepository::class)
        val refreshUsersWithoutMetadata = mock(RefreshUsersWithoutMetadataUseCase::class)

        private val addMemberUseCase = AddMemberToConversationUseCaseImpl(
            conversationGroupRepository,
            userRepository,
            refreshUsersWithoutMetadata
        )

        suspend fun withAddMembers(either: Either<CoreFailure, Unit>) = apply {
            coEvery {
                conversationGroupRepository.addMembers(any(), any())
            }.returns(either)
        }

        suspend fun withInsertOrIgnoreIncompleteUsers() = apply {
            coEvery {
                userRepository.insertOrIgnoreIncompleteUsers(any())
            }.returns(Either.Right(Unit))
        }

        fun arrange(block: Arrangement.() -> Unit = { }) = apply(block).let { this to addMemberUseCase }
    }
}
