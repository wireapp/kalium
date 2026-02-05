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
import com.wire.kalium.logic.framework.TestTeam
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GetUpdatedSelfTeamUseCaseTest {

    @Test
    fun givenSelfUserHasNotValidTeam_whenGettingSelfTeam_thenTeamInfoIsNotRequested() = runTest {
        // given
        val (arrangement, sut) = Arrangement()
            .withSelfTeamIdProvider(Either.Right(null))
            .withTeamByIdReturning(flowOf(TestTeam.TEAM))
            .arrange()

        // when
        val result = sut.invoke()

        // then
        assertNull(result)
        verifySuspend(VerifyMode.not) {
            arrangement.teamRepository.getTeam(TestTeam.TEAM_ID)
        }
    }

    @Test
    fun givenAnError_whenGettingSelfTeam_thenTeamInfoIsNotRequested() = runTest {
        // given
        val (arrangement, sut) = Arrangement()
            .withSelfTeamIdProvider(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))
            .withTeamByIdReturning(flowOf(TestTeam.TEAM))
            .arrange()

        // when
        val result = sut.invoke()

        // then
        assertNull(result)
        verifySuspend(VerifyMode.not) {
            arrangement.teamRepository.getTeam(eq(TestTeam.TEAM_ID))
        }
    }

    @Test
    fun givenSelfUserHasValidTeam_whenGettingSelfTeam_thenTeamInfoIsRequested() = runTest {
        // given
        val (arrangement, sut) = Arrangement()
            .withSelfTeamIdProvider(Either.Right(TestTeam.TEAM_ID))
            .withTeamByIdReturning(flowOf(TestTeam.TEAM))
            .arrange()

        // when
        val result = sut.invoke()

        // then
        assertNotNull(result)
        verifySuspend {
            arrangement.teamRepository.getTeam(eq(TestTeam.TEAM_ID))
        }
    }

    private class Arrangement {
        val selfTeamIdProvider: SelfTeamIdProvider = mock<SelfTeamIdProvider>()
        val teamRepository: TeamRepository = mock<TeamRepository>()

        fun withSelfTeamIdProvider(result: Either<CoreFailure, TeamId?>) = apply {
            everySuspend {
                selfTeamIdProvider.invoke()
            }.returns(result)
        }

        fun withTeamByIdReturning(result: kotlinx.coroutines.flow.Flow<Team?>) = apply {
            everySuspend {
                teamRepository.getTeam(any())
            }.returns(result)
        }

        fun arrange() = this to GetUpdatedSelfTeamUseCase(
            selfTeamIdProvider = selfTeamIdProvider,
            teamRepository = teamRepository
        )
    }
}
