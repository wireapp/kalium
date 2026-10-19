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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.shouldSucceed
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.calls
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.awaitCancellation
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncSelfTeamUseCaseTest {

    @Test
    fun givenSelfUserDoesNotHaveValidTeam_whenSyncingSelfTeam_thenTeamInfoAndServicesAreNotRequested() = runTest {
        val selfUser = TestUser.SELF.copy(teamId = null).right()

        val (arrangement, syncSelfTeamUseCase) = Arrangement()
            .withSelfUser(selfUser)
            .witFetchAllTeamMembersEagerly(200)
            .arrange()

        syncSelfTeamUseCase.invoke()

        verifySuspend(VerifyMode.not) {
            arrangement.teamRepository.fetchTeamById(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.teamRepository.fetchMembersByTeamId(any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.teamRepository.syncServices(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.teamRepository.fetchAppsByTeamId(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.teamRepository.fetchCollaboratorsByTeamId(any())
        }
    }

    @Test
    fun givenSelfUserHasValidTeamAndFetchAllTeamMembersEagerlyIsTrue_whenSyncingSelfTeam_thenTeamInfoAndServicesAreRequestedSuccessfully() =
        runTest {
            val selfUser = TestUser.SELF.right()

            val (arrangement, syncSelfTeamUseCase) = Arrangement()
                .withSelfUser(selfUser)
                .witFetchAllTeamMembersEagerly(200)
                .withTeam()
                .withTeamMembers()
                .withServicesSync()
                .withAppsSync()
                .withCollaboratorsSync()
                .arrange()

            syncSelfTeamUseCase.invoke()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.teamRepository.fetchTeamById(eq(TestUser.SELF.teamId!!))
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.teamRepository.fetchMembersByTeamId(
                    eq(TestUser.SELF.teamId!!),
                    eq(TestUser.SELF.id.domain),
                    any(),
                    any()
                )
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.teamRepository.syncServices(eq(TestUser.SELF.teamId!!))
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.teamRepository.fetchAppsByTeamId(eq(TestUser.SELF.teamId!!))
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.teamRepository.fetchCollaboratorsByTeamId(eq(TestUser.SELF.teamId!!))
            }
        }

    @Test
    fun givenFetchingTeamInfoReturnsAnError_whenSyncingSelfTeam_thenServicesAreNotSynced() = runTest {
        val selfUser = TestUser.SELF.right()

        val (arrangement, syncSelfTeamUseCase) = Arrangement()
            .withSelfUser(selfUser)
            .witFetchAllTeamMembersEagerly(null)
            .withFailingTeamInfo()
            .arrange()

        syncSelfTeamUseCase.invoke()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.teamRepository.fetchTeamById(eq(TestUser.SELF.teamId!!))
        }
        verifySuspend(VerifyMode.not) {
            arrangement.teamRepository.fetchMembersByTeamId(any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.teamRepository.syncServices(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.teamRepository.fetchAppsByTeamId(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.teamRepository.fetchCollaboratorsByTeamId(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.teamRepository.fetchMembersByTeamId(
                eq(TestUser.SELF.teamId!!),
                eq(TestUser.SELF.id.domain),
                any(),
                any()
            )
        }
    }

    @Test
    fun givenSelfUserHasValidTeamAndFetchLimitIsNull_whenSyncingSelfTeam_thenTeamInfoAndServicesAreRequestedSuccessfully() = runTest {
        val selfUser = TestUser.SELF.right()

        val (arrangement, syncSelfTeamUseCase) = Arrangement()
            .withSelfUser(selfUser)
            .witFetchAllTeamMembersEagerly(null)
            .withTeamMembers()
            .withTeam()
            .withServicesSync()
            .withAppsSync()
            .withCollaboratorsSync()
            .arrange()

        syncSelfTeamUseCase.invoke()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.teamRepository.fetchTeamById(eq(TestUser.SELF.teamId!!))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.teamRepository.fetchMembersByTeamId(any(), any(), eq<Int?>(null), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.teamRepository.syncServices(eq(TestUser.SELF.teamId!!))
        }
    }

    @Test
    fun givenAllTeamBranchesFail_whenSyncing_thenEveryBranchRunsAndSyncSucceeds() = runTest {
        val (arrangement, sync) = Arrangement()
            .withSelfUser(TestUser.SELF.right())
            .withTeam()
            .withFailingTeamMembers()
            .withFailingServicesSync()
            .arrange()
        val failure = Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.accessDenied))
        everySuspend { arrangement.teamRepository.fetchAppsByTeamId(any()) } returns failure
        everySuspend { arrangement.teamRepository.fetchCollaboratorsByTeamId(any()) } returns failure

        sync().shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) { arrangement.teamRepository.fetchMembersByTeamId(any(), any(), any(), any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.teamRepository.syncServices(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.teamRepository.fetchAppsByTeamId(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.teamRepository.fetchCollaboratorsByTeamId(any()) }
    }

    @Test
    fun givenAppsStillSyncing_whenOtherBranchesFinish_thenTeamSyncWaits() = runTest {
        val (arrangement, sync) = Arrangement()
            .withSelfUser(TestUser.SELF.right()).withTeam().withTeamMembers()
            .withServicesSync().withCollaboratorsSync().arrange()
        val gate = CompletableDeferred<Unit>()
        everySuspend { arrangement.teamRepository.fetchAppsByTeamId(any()) } calls {
            gate.await()
            Either.Right(Unit)
        }

        val result = async { sync() }
        runCurrent()
        assertFalse(result.isCompleted)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.teamRepository.fetchCollaboratorsByTeamId(any()) }
        gate.complete(Unit)
        result.await().shouldSucceed()
    }

    @Test
    fun givenTeamSyncIsCancelled_whenAppSyncIsRunning_thenChildIsDrained() = runTest {
        val (arrangement, sync) = Arrangement()
            .withSelfUser(TestUser.SELF.right()).withTeam().withTeamMembers()
            .withServicesSync().withCollaboratorsSync().arrange()
        var drained = false
        everySuspend { arrangement.teamRepository.fetchAppsByTeamId(any()) } calls {
            try {
                awaitCancellation()
            } finally {
                drained = true
            }
        }

        val result = async { sync() }
        runCurrent()
        assertFalse(result.isCompleted)
        result.cancelAndJoin()
        assertTrue(result.isCancelled)
        assertTrue(drained)
    }

    private class Arrangement {

        var fetchTeamMemberLimit: Int? = null
        val userRepository = mock<UserRepository>(mode = MockMode.autoUnit)
        val teamRepository = mock<TeamRepository>(mode = MockMode.autoUnit)

        private lateinit var syncSelfTeamUseCase: SyncSelfTeamUseCase
        fun witFetchAllTeamMembersEagerly(result: Int?) = apply {
            fetchTeamMemberLimit = result
        }

        suspend fun withSelfUser(result: Either<StorageFailure, SelfUser>) = apply {
            everySuspend {
                userRepository.getSelfUser()
            } returns result
        }

        suspend fun withTeam() = apply {
            everySuspend {
                teamRepository.fetchTeamById(any())
            } returns Either.Right(TestTeam.TEAM)
        }

        suspend fun withFailingTeamInfo() = apply {
            everySuspend {
                teamRepository.fetchTeamById(any())
            } returns Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.badRequest))
        }

        suspend fun withTeamMembers() = apply {
            everySuspend {
                teamRepository.fetchMembersByTeamId(any(), any(), any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withFailingTeamMembers() = apply {
            everySuspend {
                teamRepository.fetchMembersByTeamId(any(), any(), any(), any())
            } returns Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.accessDenied))
        }

        suspend fun withServicesSync() = apply {
            everySuspend {
                teamRepository.syncServices(any())
            } returns Either.Right(Unit)
        }

        suspend fun withAppsSync() = apply {
            everySuspend {
                teamRepository.fetchAppsByTeamId(any())
            } returns Either.Right(Unit)
        }

        suspend fun withCollaboratorsSync() = apply {
            everySuspend {
                teamRepository.fetchCollaboratorsByTeamId(any())
            } returns Either.Right(Unit)
        }

        suspend fun withFailingServicesSync() = apply {
            everySuspend {
                teamRepository.syncServices(any())
            } returns Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.accessDenied))
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
