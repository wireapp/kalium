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

package com.wire.kalium.logic.feature.team

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
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

class DeleteTeamConversationUseCaseTest {

    @Test
    fun givenAConversationId_whenInvokingADeleteConversation_thenShouldDelegateTheCallAndReturnASuccessResult() = runTest {
        val (arrangement, deleteTeamConversation) = Arrangement()
            .withGetSelfTeam()
            .withSuccessApiDeletingConversation()
            .arrange {
                withDeletingConversationSucceeding()
            }

        val result = deleteTeamConversation(TestConversation.ID)

        assertEquals(Result.Success::class, result::class)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.selfTeamIdProvider.invoke()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.teamRepository.deleteConversation(eq(TestConversation.ID), eq(TeamId(TestTeam.TEAM.id)))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.deleteConversation(any(), eq(TestConversation.ID))
        }
    }

    @Test
    fun givenAConversationId_whenInvokingADeleteConversationAndAnError_thenShouldDelegateTheCallAndReturnAFailureResult() = runTest {
        val (arrangement, deleteTeamConversation) = Arrangement()
            .withGetSelfTeam()
            .withApiErrorDeletingConversation()
            .arrange {
                withDeletingConversationSucceeding()
            }

        val result = deleteTeamConversation(TestConversation.ID)

        assertEquals(Result.Failure.GenericFailure::class, result::class)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.selfTeamIdProvider.invoke()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.teamRepository.deleteConversation(eq(TestConversation.ID), eq(TeamId(TestTeam.TEAM.id)))
        }
        verifySuspend(VerifyMode.not) {
            arrangement.deleteConversation(any(), eq(TestConversation.ID))
        }
    }

    @Test
    fun givenAConversationId_whenInvokingADeleteConversationAndAnNoTeam_thenShouldDelegateTheCallAndReturnAFailureResult() = runTest {
        val (arrangement, deleteTeamConversation) = Arrangement()
            .withGetSelfTeam(null)
            .withApiErrorDeletingConversation()
            .arrange {
                withDeletingConversationSucceeding()
            }

        val result = deleteTeamConversation(TestConversation.ID)

        assertEquals(Result.Failure.NoTeamFailure::class, result::class)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.selfTeamIdProvider.invoke()
        }
        verifySuspend(VerifyMode.not) {
            arrangement.teamRepository.deleteConversation(eq(TestConversation.ID), eq(TestTeam.TEAM_ID))
        }
        verifySuspend(VerifyMode.not) {
            arrangement.deleteConversation(any(), eq(TestConversation.ID))
        }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val selfTeamIdProvider: SelfTeamIdProvider = mock<SelfTeamIdProvider>(mode = MockMode.autoUnit)
        val teamRepository: TeamRepository = mock<TeamRepository>(mode = MockMode.autoUnit)
        val deleteConversation: DeleteConversationUseCase = mock<DeleteConversationUseCase>(mode = MockMode.autoUnit)

        suspend fun withGetSelfTeam(team: Team? = TestTeam.TEAM) = apply {
            val result = team?.id?.let {
                TeamId(it)
            }
            everySuspend {
                selfTeamIdProvider.invoke()
            } returns Either.Right(result)
        }

        suspend fun withApiErrorDeletingConversation() = apply {
            everySuspend {
                teamRepository.deleteConversation(any(), any())
            } returns Either.Left(CoreFailure.Unknown(RuntimeException("some error")))
        }

        suspend fun withSuccessApiDeletingConversation() = apply {
            everySuspend {
                teamRepository.deleteConversation(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withDeletingConversationSucceeding() = apply {
            everySuspend {
                deleteConversation(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, DeleteTeamConversationUseCase> = run {
            val useCase = DeleteTeamConversationUseCaseImpl(
                selfTeamIdProvider,
                teamRepository,
                deleteConversation,
                cryptoTransactionProvider
            )
            withTransactionReturning(Either.Right(Unit))
            block()
            this to useCase
        }
    }

}
