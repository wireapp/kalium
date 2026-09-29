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
package com.wire.kalium.logic.feature.app

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.app.AppRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SyncAppsUseCaseTest {

    @Test
    fun givenApiBelowV10_whenSyncing_thenNoOp() = runTest {
        val arrangement = Arrangement(apiVersion = 9)

        assertIs<SyncAppsUseCase.Result.Success>(arrangement.useCase())
        verifySuspend(VerifyMode.exactly(0)) { arrangement.selfTeamIdProvider() }
        verifySuspend(VerifyMode.exactly(0)) { arrangement.appRepository.syncApps(any(), any()) }
    }

    @Test
    fun givenTeamlessUser_whenSyncing_thenNoOp() = runTest {
        val arrangement = Arrangement(apiVersion = 15).withTeam(null)

        assertIs<SyncAppsUseCase.Result.Success>(arrangement.useCase())
        verifySuspend(VerifyMode.exactly(0)) { arrangement.appRepository.syncApps(any(), any()) }
    }

    @Test
    fun givenApiV10ToV14_whenSyncing_thenOnlyCollaboratorAppsAreRequested() = runTest {
        val arrangement = Arrangement(apiVersion = 14).withTeam(TEAM_ID).withSyncSuccess(includeTeamApps = false)

        assertIs<SyncAppsUseCase.Result.Success>(arrangement.useCase())
        verifySuspend { arrangement.appRepository.syncApps(TEAM_ID, includeTeamApps = false) }
    }

    @Test
    fun givenApiV15_whenSyncing_thenTeamAndCollaboratorAppsAreRequested() = runTest {
        val arrangement = Arrangement(apiVersion = 15).withTeam(TEAM_ID).withSyncSuccess(includeTeamApps = true)

        assertIs<SyncAppsUseCase.Result.Success>(arrangement.useCase())
        verifySuspend { arrangement.appRepository.syncApps(TEAM_ID, includeTeamApps = true) }
    }

    @Test
    fun givenRepositoryFailure_whenSyncing_thenFailureIsPropagated() = runTest {
        val failure = NetworkFailure.NoNetworkConnection(null)
        val arrangement = Arrangement(apiVersion = 15).withTeam(TEAM_ID).withSyncFailure(failure)

        val result = assertIs<SyncAppsUseCase.Result.Failure>(arrangement.useCase())
        assertEquals(failure, result.error)
    }

    private class Arrangement(apiVersion: Int) {
        val appRepository = mock<AppRepository>()
        val selfTeamIdProvider = mock<SelfTeamIdProvider>()
        val useCase = SyncAppsUseCaseImpl(appRepository, selfTeamIdProvider, apiVersion)

        fun withTeam(teamId: TeamId?) = apply {
            everySuspend { selfTeamIdProvider() } returns Either.Right(teamId)
        }

        fun withSyncSuccess(includeTeamApps: Boolean) = apply {
            everySuspend { appRepository.syncApps(TEAM_ID, includeTeamApps) } returns Either.Right(Unit)
        }

        fun withSyncFailure(failure: NetworkFailure) = apply {
            everySuspend { appRepository.syncApps(TEAM_ID, true) } returns Either.Left(failure)
        }
    }

    private companion object {
        val TEAM_ID = TeamId("team-id")
    }
}
