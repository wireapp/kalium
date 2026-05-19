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
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

        assertEquals(SyncServicesUseCase.Result.Success, result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.teamRepository.syncServices(teamId)
        }
    }

    @Test
    fun givenNoTeamId_whenInvoked_thenReturnsRightUnitWithoutSyncingServices() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSelfUserTeamId(Either.Right(null))
            .arrange()

        val result = useCase()

        assertEquals(SyncServicesUseCase.Result.Success, result)
        verifySuspend(VerifyMode.not) {
            arrangement.teamRepository.syncServices(any())
        }
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

        assertTrue(result is SyncServicesUseCase.Result.Failure)
        assertEquals(failure, result.error)
    }

    private class Arrangement {
        val teamRepository: TeamRepository = mock()
        val selfTeamIdProvider: SelfTeamIdProvider = mock()

        private val useCase: SyncServicesUseCase = SyncServicesUseCaseImpl(
            teamRepository = teamRepository,
            selfTeamIdProvider = selfTeamIdProvider
        )

        fun withSelfUserTeamId(either: Either<CoreFailure, TeamId?>) = apply {
            everySuspend {
                selfTeamIdProvider()
            } returns (either)
        }

        fun withSyncingServices(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                teamRepository.syncServices(any())
            } returns (result)
        }

        fun arrange() = this to useCase
    }
}
