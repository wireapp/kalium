/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncSelfTeamUseCaseTest {

    @Test
    fun givenSelfUserDoesNotHaveValidTeam_whenSyncingSelfTeam_thenTeamInfoAndServicesAreNotRequested() = runTest {
        // given
        val selfUserFlow = flowOf(
            TestUser.SELF.copy(
                teamId = null
            )
        )

        val (arrangement, syncSelfTeamUseCase) = Arrangement()
            .withSelfUser(selfUserFlow)
            .arrange()

        // when
        syncSelfTeamUseCase.invoke()

        // then
        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::fetchTeamById)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::syncServices)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenSelfUserHasValidTeam_whenSyncingSelfTeam_thenTeamInfoAndServicesAreRequestedSuccessfully() = runTest {
        // given
        val selfUserFlow = flowOf(TestUser.SELF)

        val (arrangement, syncSelfTeamUseCase) = Arrangement()
            .withSelfUser(selfUserFlow)
            .withTeam()
            .withServicesSync()
            .arrange()

        // when
        syncSelfTeamUseCase.invoke()

        // then
        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::fetchTeamById)
            .with(eq(TestUser.SELF.teamId))
            .wasInvoked(exactly = once)

        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::syncServices)
            .with(eq(TestUser.SELF.teamId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenFetchingTeamInfoReturnsAnError_whenSyncingSelfTeam_thenServicesAreNotSynced() = runTest {
        // given
        val selfUserFlow = flowOf(TestUser.SELF)

        val (arrangement, syncSelfTeamUseCase) = Arrangement()
            .withSelfUser(selfUserFlow)
            .withFailingTeamInfo()
            .arrange()

        // when
        syncSelfTeamUseCase.invoke()

        // then
        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::fetchTeamById)
            .with(eq(TestUser.SELF.teamId))
            .wasInvoked(exactly = once)

        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::syncServices)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenServicesReturnAccessDenied_whenSyncingSelfTeam_thenServicesAreIgnoredButUseCaseSucceeds() = runTest {
        // given
        val selfUserFlow = flowOf(TestUser.SELF)

        val (_, syncSelfTeamUseCase) = Arrangement()
            .withSelfUser(selfUserFlow)
            .withTeam()
            .withFailingServicesSync()
            .arrange()

        // when
        val result = syncSelfTeamUseCase.invoke()

        // then
        result.shouldSucceed()
    }

    private class Arrangement {

        @Mock
        val userRepository = mock(classOf<UserRepository>())

        @Mock
        val teamRepository = mock(classOf<TeamRepository>())

        val syncSelfTeamUseCase = SyncSelfTeamUseCaseImpl(
            userRepository = userRepository,
            teamRepository = teamRepository
        )

        fun withSelfUser(selfUserFlow: Flow<SelfUser>) = apply {
            given(userRepository)
                .suspendFunction(userRepository::observeSelfUser)
                .whenInvoked()
                .thenReturn(selfUserFlow)
        }

        fun withTeam() = apply {
            given(teamRepository)
                .suspendFunction(teamRepository::fetchTeamById)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(TestTeam.TEAM))
        }

        fun withFailingTeamInfo() = apply {
            given(teamRepository)
                .suspendFunction(teamRepository::fetchTeamById)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.badRequest)))
        }

        fun withServicesSync() = apply {
            given(teamRepository)
                .suspendFunction(teamRepository::syncServices)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withFailingServicesSync() = apply {
            given(teamRepository)
                .suspendFunction(teamRepository::syncServices)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.accessDenied)))
        }

        fun arrange() = this to syncSelfTeamUseCase

        companion object {

        }
    }
}
