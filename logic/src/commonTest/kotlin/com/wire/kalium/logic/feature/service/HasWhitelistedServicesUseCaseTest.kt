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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.team.TeamRepository
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class HasWhitelistedServicesUseCaseTest {
    private val teamRepository: TeamRepository = mock()
    private val selfTeamIdProvider: SelfTeamIdProvider = mock()
    private val useCase = HasWhitelistedServicesUseCaseImpl(teamRepository, selfTeamIdProvider)
    private val teamId = TeamId("team")
    private val failure = NetworkFailure.NoNetworkConnection(null)

    @Test
    fun givenTeamWhitelist_whenChecking_thenReturnItsCurrentAvailability() = runTest {
        everySuspend { selfTeamIdProvider() } returns Either.Right(teamId)
        listOf(true, false).forEach { available ->
            everySuspend { teamRepository.hasWhitelistedServices(teamId) } returns Either.Right(available)
            assertEquals(available, useCase())
        }
    }

    @Test
    fun givenLookupFailure_whenChecking_thenReturnFalse() = runTest {
        everySuspend { selfTeamIdProvider() } returns Either.Right(teamId)
        everySuspend { teamRepository.hasWhitelistedServices(teamId) } returns Either.Left(failure)
        assertFalse(useCase())
    }

    @Test
    fun givenMissingOrFailedTeam_whenChecking_thenDoNotQueryWhitelist() = runTest {
        everySuspend { selfTeamIdProvider() } returns Either.Right(null)
        assertFalse(useCase())
        everySuspend { selfTeamIdProvider() } returns Either.Left(failure)
        assertFalse(useCase())
        verifySuspend(VerifyMode.not) { teamRepository.hasWhitelistedServices(any()) }
    }

    @Test
    fun givenCancellation_whenChecking_thenPropagateCancellation() = runTest {
        everySuspend { selfTeamIdProvider() } returns Either.Right(teamId)
        everySuspend { teamRepository.hasWhitelistedServices(teamId) } throws CancellationException("Cancelled")
        assertFailsWith<CancellationException> { useCase() }
    }
}
