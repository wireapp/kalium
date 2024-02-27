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
import kotlin.properties.Delegates
import kotlin.test.Test

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
            .witFetchAllTeamMembersEagerly(200)
            .arrange()

        // when
        syncSelfTeamUseCase.invoke()

        // then
        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::fetchTeamById)
            .with(any())
            .wasNotInvoked()
        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::fetchMembersByTeamId)
            .with(any(), any())
            .wasNotInvoked()
        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::syncServices)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenSelfUserHasValidTeamAndFetchAllTeamMembersEagerlyIsTrue_whenSyncingSelfTeam_thenTeamInfoAndServicesAreRequestedSuccessfully() = runTest {
        // given
        val selfUserFlow = flowOf(TestUser.SELF)

        val (arrangement, syncSelfTeamUseCase) = Arrangement()
            .withSelfUser(selfUserFlow)
            .witFetchAllTeamMembersEagerly(200)
            .withTeam()
            .withTeamMembers()
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
            .suspendFunction(arrangement.teamRepository::fetchMembersByTeamId)
            .with(
                eq(TestUser.SELF.teamId),
                eq(TestUser.SELF.id.domain)
            )
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
            .witFetchAllTeamMembersEagerly(null)
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
            .suspendFunction(arrangement.teamRepository::fetchMembersByTeamId)
            .with(any(), any())
            .wasNotInvoked()
        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::syncServices)
            .with(any())
            .wasNotInvoked()
        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::fetchMembersByTeamId)
            .with(
                eq(TestUser.SELF.teamId),
                eq(TestUser.SELF.id.domain)
            ).wasNotInvoked()
    }

    @Test
    fun givenServicesReturnAccessDenied_whenSyncingSelfTeam_thenServicesAreIgnoredButUseCaseSucceeds() = runTest {
        // given
        val selfUserFlow = flowOf(TestUser.SELF)

        val (_, syncSelfTeamUseCase) = Arrangement()
            .withSelfUser(selfUserFlow)
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
        val selfUserFlow = flowOf(TestUser.SELF)

        val (arrangement, syncSelfTeamUseCase) = Arrangement()
            .withSelfUser(selfUserFlow)
            .witFetchAllTeamMembersEagerly(null)
            .withTeamMembers()
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
            .suspendFunction(arrangement.teamRepository::fetchMembersByTeamId)
            .with(any(), any(), eq(null))
            .wasInvoked(exactly = once)

        verify(arrangement.teamRepository)
            .suspendFunction(arrangement.teamRepository::syncServices)
            .with(eq(TestUser.SELF.teamId))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        var fetchTeamMemberLimit: Int? = null

        @Mock
        val userRepository = mock(classOf<UserRepository>())

        @Mock
        val teamRepository = mock(classOf<TeamRepository>())

        private lateinit var syncSelfTeamUseCase: SyncSelfTeamUseCase
        fun witFetchAllTeamMembersEagerly(result: Int?) = apply {
            fetchTeamMemberLimit = result
        }

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

        fun withTeamMembers() = apply {
            given(teamRepository)
                .suspendFunction(teamRepository::fetchMembersByTeamId)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
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
