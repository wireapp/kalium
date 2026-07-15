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
package com.wire.kalium.cells.domain.usecase

import com.wire.kalium.cells.domain.CellConversationRepository
import com.wire.kalium.cells.domain.CellUsersRepository
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.user.UserId
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsSelfGuestInConversationUseCaseTest {

    private companion object {
        private const val CONVERSATION_ID = "conv1@wire.com"
        private val SELF_USER_ID = UserId("selfUserId", "domain")
        private const val SELF_TEAM = "selfTeam"
        private const val OTHER_TEAM = "otherTeam"
    }

    @Test
    fun givenConversationOwnedByAnotherTeam_whenInvoked_thenReturnTrue() = runTest {
        val useCase = Arrangement()
            .withConversationTeamId(OTHER_TEAM)
            .withSelfTeamId(SELF_TEAM)
            .arrange()

        assertTrue(useCase(CONVERSATION_ID))
    }

    @Test
    fun givenConversationOwnedBySelfTeam_whenInvoked_thenReturnFalse() = runTest {
        val useCase = Arrangement()
            .withConversationTeamId(SELF_TEAM)
            .withSelfTeamId(SELF_TEAM)
            .arrange()

        assertFalse(useCase(CONVERSATION_ID))
    }

    @Test
    fun givenSelfHasNoTeamAndConversationHasTeam_whenInvoked_thenReturnTrue() = runTest {
        val useCase = Arrangement()
            .withConversationTeamId(OTHER_TEAM)
            .withSelfTeamId(null)
            .arrange()

        assertTrue(useCase(CONVERSATION_ID))
    }

    @Test
    fun givenConversationHasNoTeam_whenInvoked_thenReturnFalse() = runTest {
        val useCase = Arrangement()
            .withConversationTeamId(null)
            .withSelfTeamId(SELF_TEAM)
            .arrange()

        assertFalse(useCase(CONVERSATION_ID))
    }

    @Test
    fun givenConversationRepositoryFails_whenInvoked_thenReturnFalse() = runTest {
        val useCase = Arrangement()
            .withConversationTeamIdError(StorageFailure.DataNotFound)
            .arrange()

        assertFalse(useCase(CONVERSATION_ID))
    }

    @Test
    fun givenGuestConversation_whenInvokedTwice_thenConversationTeamIsResolvedEachCall() = runTest {
        val arrangement = Arrangement()
            .withConversationTeamId(OTHER_TEAM)
            .withSelfTeamId(SELF_TEAM)
        val useCase = arrangement.arrange()

        assertEquals(true, useCase(CONVERSATION_ID))
        assertEquals(true, useCase(CONVERSATION_ID))
    }

    private class Arrangement {
        private val usersRepository = mock<CellUsersRepository>()
        private val conversationRepository = mock<CellConversationRepository>()

        private var conversationTeamId: Either<StorageFailure, String?> = Either.Right(null)
        private var selfTeamId: Either<StorageFailure, String?> = Either.Right(null)

        fun withConversationTeamId(teamId: String?) = apply {
            conversationTeamId = teamId.right()
        }

        fun withConversationTeamIdError(failure: StorageFailure) = apply {
            conversationTeamId = failure.left()
        }

        fun withSelfTeamId(teamId: String?) = apply {
            selfTeamId = teamId.right()
        }

        fun arrange(): IsSelfGuestInConversationUseCase {
            everySuspend { conversationRepository.getConversationTeamId(any()) }.returns(conversationTeamId)
            everySuspend { usersRepository.getUserTeamId(any()) }.returns(selfTeamId)
            return IsSelfGuestInConversationUseCaseImpl(
                selfUserId = SELF_USER_ID,
                usersRepository = usersRepository,
                conversationRepository = conversationRepository,
            )
        }
    }
}
