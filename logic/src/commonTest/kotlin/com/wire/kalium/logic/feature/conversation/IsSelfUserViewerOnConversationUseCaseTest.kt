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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsSelfUserViewerOnConversationUseCaseTest {

    @Test
    fun givenNonCellsGroupConversation_whenChecking_thenReturnsTrue() =
        runTest(TestKaliumDispatcher.main) {
            val conversation = TestConversation.GROUP().copy(teamId = TeamId("some-team"))
            val conversationDetails = ConversationDetails.Group.Regular(
                conversation = conversation,
                isSelfUserMember = true,
                selfRole = Conversation.Member.Role.Member,
                wireCell = null,
            )
            val selfUser = TestUser.SELF.copy(teamId = TeamId("some-team"))

            val (_, useCase) = Arrangement()
                .withSelfTeamIdProviderReturning(Either.Right(selfUser.teamId))
                .withConversationDetailsReturning(Either.Right(conversationDetails))
                .arrange()

            val result = useCase(TestConversation.ID)

            assertTrue(result)
        }

    @Test
    fun givenCellsConversationOwnedBySelfTeam_whenChecking_thenReturnsTrue() =
        runTest(TestKaliumDispatcher.main) {
            val selfTeamId = TeamId("self-team")
            val conversation = TestConversation.GROUP().copy(teamId = selfTeamId)
            val conversationDetails = ConversationDetails.Group.Regular(
                conversation = conversation,
                isSelfUserMember = true,
                selfRole = Conversation.Member.Role.Member,
                wireCell = "wire-cell-id",
            )
            val selfUser = TestUser.SELF.copy(teamId = selfTeamId)

            val (_, useCase) = Arrangement()
                .withSelfTeamIdProviderReturning(Either.Right(selfUser.teamId))
                .withConversationDetailsReturning(Either.Right(conversationDetails))
                .arrange()

            val result = useCase(TestConversation.ID)

            assertTrue(result)
        }

    @Test
    fun givenCellsConversationOwnedByForeignTeam_whenChecking_thenReturnsFalse() =
        runTest(TestKaliumDispatcher.main) {
            val selfTeamId = TeamId("self-team")
            val foreignTeamId = TeamId("foreign-team")
            val conversation = TestConversation.GROUP().copy(teamId = foreignTeamId)
            val conversationDetails = ConversationDetails.Group.Regular(
                conversation = conversation,
                isSelfUserMember = true,
                selfRole = Conversation.Member.Role.Member,
                wireCell = "wire-cell-id",
            )
            val selfUser = TestUser.SELF.copy(teamId = selfTeamId)

            val (_, useCase) = Arrangement()
                .withSelfTeamIdProviderReturning(Either.Right(selfUser.teamId))
                .withConversationDetailsReturning(Either.Right(conversationDetails))
                .arrange()

            val result = useCase(TestConversation.ID)

            assertFalse(result)
        }

    @Test
    fun givenNonGroupConversation_whenChecking_thenReturnsTrue() =
        runTest(TestKaliumDispatcher.main) {
            val conversationDetails = ConversationDetails.OneOne(
                conversation = TestConversation.ONE_ON_ONE(),
                otherUser = TestUser.OTHER,
                userType = TestUser.OTHER.userType,
            )
            val selfUser = TestUser.SELF
            val (_, useCase) = Arrangement()
                .withSelfTeamIdProviderReturning(Either.Right(selfUser.teamId))
                .withConversationDetailsReturning(Either.Right(conversationDetails))
                .arrange()

            val result = useCase(TestConversation.ID)

            assertTrue(result)
        }

    @Test
    fun givenConversationRepositoryFailure_whenObserving_thenReturnsTrue() =
        runTest(TestKaliumDispatcher.main) {
            val selfUser = TestUser.SELF

            val (_, useCase) = Arrangement()
                .withSelfTeamIdProviderReturning(Either.Right(selfUser.teamId))
                .withConversationDetailsReturning(Either.Left(StorageFailure.DataNotFound))
                .arrange()

            val result = useCase(TestConversation.ID)

            assertTrue(result)
        }

    private class Arrangement {

        private val conversationRepository: ConversationRepository = mock()
        private val selfTeamIdProvider: SelfTeamIdProvider = mock()

        private val isSelfUserViewerOnConversation = IsSelfUserViewerOnConversationUseCase(
            conversationRepository = conversationRepository,
            selfTeamIdProvider = selfTeamIdProvider,
            dispatcher = TestKaliumDispatcher,
        )

        fun withSelfTeamIdProviderReturning(result: Either<CoreFailure, TeamId?>) = apply {
            everySuspend { selfTeamIdProvider() } returns result
        }

        fun withConversationDetailsReturning(result: Either<StorageFailure, ConversationDetails>) = apply {
            everySuspend { conversationRepository.observeConversationDetailsById(any()) } returns
                    flowOf(result)
        }

        fun arrange() = this to isSelfUserViewerOnConversation
    }
}
