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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class GetUpdatedSelfTeamUseCaseTest {

    @Test
    fun givenSelfUserHaveValidTeam_whenGettingSelfTeam_thenTeamInfoAndServicesAreRequested() = runTest {
        // given
        val (arrangement, sut) = Arrangement()
            .withSelfTeamIdProvider()
            .withFetchingIdReturning(Either.Right(TestTeam.TEAM))
            .arrange()

        // when
        val result = sut.invoke()

        // then
        result.shouldSucceed()
        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::fetchTeamById)
            .with(eq(TestTeam.TEAM_ID))
            .wasInvoked()
    }

    private class Arrangement() {
        @Mock
        val selfTeamIdProvider: SelfTeamIdProvider = mock(SelfTeamIdProvider::class)

        @Mock
        val teamRepository: TeamRepository = mock(TeamRepository::class)

        fun withSelfTeamIdProvider() = apply {
            given(selfTeamIdProvider)
                .suspendFunction(selfTeamIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(TestTeam.TEAM_ID))
        }

        fun withFetchingIdReturning(result: Either<CoreFailure, Team>) = apply {
            given(teamRepository)
                .suspendFunction(teamRepository::fetchTeamById)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun arrange() = this to GetUpdatedSelfTeamUseCase(
            selfTeamIdProvider = selfTeamIdProvider,
            teamRepository = teamRepository
        )
    }
}
