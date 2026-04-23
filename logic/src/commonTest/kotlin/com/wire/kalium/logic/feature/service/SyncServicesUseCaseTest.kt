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
package com.wire.kalium.logic.feature.service

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncServicesUseCaseTest {

    @Test
    fun givenTeamId_whenInvoked_thenCallsTeamRepositorySyncServicesWithThatTeamId() = runTest {
        val teamId = TestUser.SELF.teamId!!
        val (arrangement, useCase) = Arrangement()
            .withSelfUserTeamId(Either.Right(teamId))
            .withSyncingServices(Either.Right(Unit))
            .arrange()

        val result = useCase()

        assertEquals(Either.Right(Unit), result)
        coVerify {
            arrangement.teamRepository.syncServices(eq(teamId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenNoTeamId_whenInvoked_thenReturnsRightUnitWithoutSyncingServices() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSelfUserTeamId(Either.Right(null))
            .arrange()

        val result = useCase()

        assertEquals(Either.Right(Unit), result)
        coVerify {
            arrangement.teamRepository.syncServices(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenSyncFails_whenInvoked_thenPropagatesFailure() = runTest {
        val teamId = TestUser.SELF.teamId!!
        val failure = NetworkFailure.NoNetworkConnection(cause = null)
        val (_, useCase) = Arrangement()
            .withSelfUserTeamId(Either.Right(teamId))
            .withSyncingServices(Either.Left(failure))
            .arrange()

        val result = useCase()

        assertTrue(result is Either.Left)
        assertEquals(failure, (result as Either.Left).value)
    }

    private class Arrangement {
        val teamRepository: TeamRepository = mock(TeamRepository::class)
        val selfTeamIdProvider = mock(SelfTeamIdProvider::class)

        private val useCase: SyncServicesUseCase = SyncServicesUseCaseImpl(
            teamRepository = teamRepository,
            selfTeamIdProvider = selfTeamIdProvider
        )

        suspend fun withSelfUserTeamId(either: Either<CoreFailure, TeamId?>) = apply {
            coEvery {
                selfTeamIdProvider.invoke()
            }.returns(either)
        }

        suspend fun withSyncingServices(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                teamRepository.syncServices(any())
            }.returns(result)
        }

        fun arrange() = this to useCase
    }
}
