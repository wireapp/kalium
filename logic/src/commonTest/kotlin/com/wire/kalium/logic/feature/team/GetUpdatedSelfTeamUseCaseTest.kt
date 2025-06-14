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
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class GetUpdatedSelfTeamUseCaseTest {

    @Test
    fun givenSelfUserHasNotValidTeam_whenGettingSelfTeam_thenTeamInfoAndServicesAreNotRequested() = runTest {
        // given
        val (arrangement, sut) = Arrangement()
            .withSelfTeamIdProvider(Either.Right(null))
            .withSyncingByIdReturning(Either.Right(TestTeam.TEAM))
            .arrange()

        // when
        val result = sut.invoke()

        // then
        result.shouldSucceed()
        coVerify {
            arrangement.teamRepository.fetchTeamById(eq(TestTeam.TEAM_ID))
        }.wasNotInvoked()
    }

    @Test
    fun givenAnError_whenGettingSelfTeam_thenTeamInfoAndServicesAreNotRequested() = runTest {
        // given
        val (arrangement, sut) = Arrangement()
            .withSelfTeamIdProvider(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))
            .withSyncingByIdReturning(Either.Right(TestTeam.TEAM))
            .arrange()

        // when
        val result = sut.invoke()

        // then
        result.shouldFail()
        coVerify {
            arrangement.teamRepository.fetchTeamById(eq(TestTeam.TEAM_ID))
        }.wasNotInvoked()
    }

    @Test
    fun givenSelfUserHasValidTeam_whenGettingSelfTeam_thenTeamInfoAndServicesAreRequested() = runTest {
        // given
        val (arrangement, sut) = Arrangement()
            .withSelfTeamIdProvider(Either.Right(TestTeam.TEAM_ID))
            .withSyncingByIdReturning(Either.Right(TestTeam.TEAM))
            .arrange()

        // when
        val result = sut.invoke()

        // then
        result.shouldSucceed()
        coVerify {
            arrangement.teamRepository.syncTeam(eq(TestTeam.TEAM_ID))
        }.wasInvoked()
    }

    private class Arrangement {
                val selfTeamIdProvider: SelfTeamIdProvider = mock(SelfTeamIdProvider::class)
        val teamRepository: TeamRepository = mock(TeamRepository::class)

        suspend fun withSelfTeamIdProvider(result: Either<CoreFailure, TeamId?>) = apply {
            coEvery {
                selfTeamIdProvider.invoke()
            }.returns(result)
        }

        suspend fun withSyncingByIdReturning(result: Either<CoreFailure, Team>) = apply {
            coEvery {
                teamRepository.syncTeam(any())
            }.returns(result)
        }

        fun arrange() = this to GetUpdatedSelfTeamUseCase(
            selfTeamIdProvider = selfTeamIdProvider,
            teamRepository = teamRepository
        )
    }
}
