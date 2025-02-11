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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SyncSelfTeamUseCaseTest {

    @Test
    fun givenSelfUserDoesNotHaveValidTeam_whenSyncingSelfTeam_thenTeamInfoAndServicesAreNotRequested() = runTest {
        // given
        val selfUser = TestUser.SELF.copy(teamId = null).right()

        val (arrangement, syncSelfTeamUseCase) = Arrangement()
            .withSelfUser(selfUser)
            .witFetchAllTeamMembersEagerly(200)
            .arrange()

        // when
        syncSelfTeamUseCase.invoke()

        // then
        coVerify {
            arrangement.teamRepository.fetchTeamById(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.teamRepository.fetchMembersByTeamId(any(), any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.teamRepository.syncServices(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenSelfUserHasValidTeamAndFetchAllTeamMembersEagerlyIsTrue_whenSyncingSelfTeam_thenTeamInfoAndServicesAreRequestedSuccessfully() =
        runTest {
            // given
            val selfUser = TestUser.SELF.right()

            val (arrangement, syncSelfTeamUseCase) = Arrangement()
                .withSelfUser(selfUser)
                .witFetchAllTeamMembersEagerly(200)
                .withTeam()
                .withTeamMembers()
                .withServicesSync()
                .arrange()

            // when
            syncSelfTeamUseCase.invoke()

            // then
            coVerify {
                arrangement.teamRepository.fetchTeamById(eq(TestUser.SELF.teamId!!))
            }.wasInvoked(exactly = once)
            coVerify {
                arrangement.teamRepository.fetchMembersByTeamId(
                    eq(TestUser.SELF.teamId!!),
                    eq(TestUser.SELF.id.domain),
                    any(),
                    any()
                )
            }.wasInvoked(exactly = once)
            coVerify {
                arrangement.teamRepository.syncServices(eq(TestUser.SELF.teamId!!))
            }.wasInvoked(exactly = once)
        }

    @Test
    fun givenFetchingTeamInfoReturnsAnError_whenSyncingSelfTeam_thenServicesAreNotSynced() = runTest {
        // given
        val selfUser = TestUser.SELF.right()

        val (arrangement, syncSelfTeamUseCase) = Arrangement()
            .withSelfUser(selfUser)
            .witFetchAllTeamMembersEagerly(null)
            .withFailingTeamInfo()
            .arrange()

        // when
        syncSelfTeamUseCase.invoke()

        // then
        coVerify {
            arrangement.teamRepository.fetchTeamById(eq(TestUser.SELF.teamId!!))
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.teamRepository.fetchMembersByTeamId(any(), any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.teamRepository.syncServices(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.teamRepository.fetchMembersByTeamId(
                eq(TestUser.SELF.teamId!!),
                eq(TestUser.SELF.id.domain),
                any(),
                any()
            )
        }.wasNotInvoked()
    }

    @Test
    fun givenServicesReturnAccessDenied_whenSyncingSelfTeam_thenServicesAreIgnoredButUseCaseSucceeds() = runTest {
        // given
        val selfUser = TestUser.SELF.right()

        val (_, syncSelfTeamUseCase) = Arrangement()
            .withSelfUser(selfUser)
            .witFetchAllTeamMembersEagerly(null)
            .withTeam()
            .withTeamMembers()
            .withFailingServicesSync()
            .arrange()

        // when
        val result = syncSelfTeamUseCase.invoke()

        // then
        result.shouldSucceed()
    }

    @Test
    fun givenSelfUserHasValidTeamAndFetchLimitIsNull_whenSyncingSelfTeam_thenTeamInfoAndServicesAreRequestedSuccessfully() = runTest {
        // given
        val selfUser = TestUser.SELF.right()

        val (arrangement, syncSelfTeamUseCase) = Arrangement()
            .withSelfUser(selfUser)
            .witFetchAllTeamMembersEagerly(null)
            .withTeamMembers()
            .withTeam()
            .withServicesSync()
            .arrange()

        // when
        syncSelfTeamUseCase.invoke()

        // then
        coVerify {
            arrangement.teamRepository.fetchTeamById(eq(TestUser.SELF.teamId!!))
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.teamRepository.fetchMembersByTeamId(any(), any(), eq<Int?>(null), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.teamRepository.syncServices(eq(TestUser.SELF.teamId!!))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        var fetchTeamMemberLimit: Int? = null

        @Mock
        val userRepository = mock(UserRepository::class)

        @Mock
        val teamRepository = mock(TeamRepository::class)

        private lateinit var syncSelfTeamUseCase: SyncSelfTeamUseCase
        fun witFetchAllTeamMembersEagerly(result: Int?) = apply {
            fetchTeamMemberLimit = result
        }

        suspend fun withSelfUser(result: Either<StorageFailure, SelfUser>) = apply {
            coEvery {
                userRepository.getSelfUser()
            }.returns(result)
        }

        suspend fun withTeam() = apply {
            coEvery {
                teamRepository.fetchTeamById(any())
            }.returns(Either.Right(TestTeam.TEAM))
        }

        suspend fun withFailingTeamInfo() = apply {
            coEvery {
                teamRepository.fetchTeamById(any())
            }.returns(Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.badRequest)))
        }

        suspend fun withTeamMembers() = apply {
            coEvery {
                teamRepository.fetchMembersByTeamId(any(), any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withServicesSync() = apply {
            coEvery {
                teamRepository.syncServices(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withFailingServicesSync() = apply {
            coEvery {
                teamRepository.syncServices(any())
            }.returns(Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.accessDenied)))
        }

        fun arrange(): Pair<Arrangement, SyncSelfTeamUseCase> {
            syncSelfTeamUseCase = SyncSelfTeamUseCaseImpl(
                userRepository = userRepository,
                teamRepository = teamRepository,
                fetchedUsersLimit = fetchTeamMemberLimit
            )
            return this to syncSelfTeamUseCase
        }
    }
}
