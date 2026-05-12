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
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.conversation.ResetMLSConversationResult
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.user.UserRepository
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationGroupRepository.addMembers(eq(listOf(TestConversation.USER_1)), eq(TestConversation.ID))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.insertOrIgnoreIncompleteUsers(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.refreshUsersWithoutMetadata.invoke()
        }
    }

    @Test
    fun givenMemberAndConversation_WhenAddMemberFailed_ThenReturnFailure() = runTest {
        val (arrangement, addMemberUseCase) = Arrangement()
            .withAddMembers(Either.Left(StorageFailure.DataNotFound))
            .withInsertOrIgnoreIncompleteUsers()
            .arrange()

        val result = addMemberUseCase(TestConversation.ID, listOf(TestConversation.USER_1))
        assertIs<AddMemberToConversationUseCase.Result.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationGroupRepository.addMembers(eq(listOf(TestConversation.USER_1)), eq(TestConversation.ID))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.insertOrIgnoreIncompleteUsers(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.refreshUsersWithoutMetadata.invoke()
        }
    }

    private class Arrangement {
        val userRepository = mock<UserRepository>(mode = MockMode.autoUnit)
        val conversationGroupRepository = mock<ConversationGroupRepository>(mode = MockMode.autoUnit)
        val refreshUsersWithoutMetadata = mock<RefreshUsersWithoutMetadataUseCase>(mode = MockMode.autoUnit)
        val resetMLSConversationUseCase = mock<ResetMLSConversationUseCase>(mode = MockMode.autoUnit)

        private val addMemberUseCase = AddMemberToConversationUseCaseImpl(
            conversationGroupRepository,
            userRepository,
            refreshUsersWithoutMetadata,
            resetMLSConversationUseCase,
        )

        suspend fun withAddMembers(either: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                conversationGroupRepository.addMembers(any(), any())
            } returns either
        }

        suspend fun withInsertOrIgnoreIncompleteUsers() = apply {
            everySuspend {
                userRepository.insertOrIgnoreIncompleteUsers(any())
            } returns Either.Right(Unit)
        }

        suspend fun arrange(block: Arrangement.() -> Unit = { }): Pair<Arrangement, AddMemberToConversationUseCase> {

            everySuspend {
                resetMLSConversationUseCase(any())
            } returns ResetMLSConversationResult.Success

            return apply(block).let { this to addMemberUseCase }
        }
    }
}
