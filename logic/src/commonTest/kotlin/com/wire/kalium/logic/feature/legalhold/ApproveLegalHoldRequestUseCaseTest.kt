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
package com.wire.kalium.logic.feature.legalhold

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.utils.io.errors.IOException
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class ApproveLegalHoldRequestUseCaseTest {

    @Test
    fun givenApproveLegalHoldParams_whenApproving_thenTheRepositoryShouldBeCalledWithCorrectParameters() = runTest {
        // given
        val selfTeamId = TeamId(TestTeam.TEAM.id)
        val password = "password"
        val (arrangement, useCase) = Arrangement()
            .withGetSelfTeamResult(Either.Right(selfTeamId))
            .withApproveLegalHoldResult(Either.Right(Unit))
            .arrange()
        // when
        useCase.invoke(password)
        // then
        coVerify {
            arrangement.teamRepository.approveLegalHoldRequest(eq(selfTeamId), eq(password))
        }.wasInvoked(once)
    }

    @Test
    fun givenGetSelfTeamIdFails_whenApproving_thenDataNotFoundErrorShouldBeReturned() = runTest {
        // given
        val password = "password"
        val (_, useCase) = Arrangement()
            .withGetSelfTeamResult(Either.Left(StorageFailure.DataNotFound))
            .arrange()
        // when
        val result = useCase.invoke(password)
        // then
        assertIs<ApproveLegalHoldRequestUseCase.Result.Failure.GenericFailure>(result)
        assertSame(StorageFailure.DataNotFound, result.coreFailure)
    }

    @Test
    fun givenApproveFailsDueToGenericError_whenApproving_thenGenericErrorShouldBeReturned() = runTest {
        // given
        val selfTeamId = TeamId(TestTeam.TEAM.id)
        val password = "password"
        val failure = NetworkFailure.ServerMiscommunication(KaliumException.GenericError(IOException("no internet")))
        val (_, useCase) = Arrangement()
            .withGetSelfTeamResult(Either.Right(selfTeamId))
            .withApproveLegalHoldResult(Either.Left(failure))
            .arrange()
        // when
        val result = useCase(password)
        // then
        assertIs<ApproveLegalHoldRequestUseCase.Result.Failure.GenericFailure>(result)
        assertSame(failure, result.coreFailure)
    }

    @Test
    fun givenApproveFailsDueToBadRequest_whenApproving_thenInvalidPasswordErrorShouldBeReturned() = runTest {
        // given
        val selfTeamId = TeamId(TestTeam.TEAM.id)
        val password = "password"
        val failure = NetworkFailure.ServerMiscommunication(TestNetworkException.badRequest)
        val (_, useCase) = Arrangement()
            .withGetSelfTeamResult(Either.Right(selfTeamId))
            .withApproveLegalHoldResult(Either.Left(failure))
            .arrange()
        // when
        val result = useCase(password)
        // then
        assertIs<ApproveLegalHoldRequestUseCase.Result.Failure.InvalidPassword>(result)
    }

    @Test
    fun givenApproveFailsDueToAccessDenied_whenDeleting_thenPasswordRequiredErrorShouldBeReturned() = runTest {
        // given
        val selfTeamId = TeamId(TestTeam.TEAM.id)
        val failure = NetworkFailure.ServerMiscommunication(TestNetworkException.accessDenied)
        val (_, useCase) = Arrangement()
            .withGetSelfTeamResult(Either.Right(selfTeamId))
            .withApproveLegalHoldResult(Either.Left(failure))
            .arrange()
        // when
        val result = useCase(null)
        // then
        assertIs<ApproveLegalHoldRequestUseCase.Result.Failure.PasswordRequired>(result)
    }

    @Test
    fun givenApproveSucceeds_whenApproving_thenSuccessShouldBeReturned() = runTest {
        // given
        val selfTeamId = TeamId(TestTeam.TEAM.id)
        val password = "password"
        val (_, useCase) = Arrangement()
            .withGetSelfTeamResult(Either.Right(selfTeamId))
            .withApproveLegalHoldResult(Either.Right(Unit))
            .arrange()
        // when
        val result = useCase(password)
        // then
        assertIs<ApproveLegalHoldRequestUseCase.Result.Success>(result)
    }

    private class Arrangement {

        @Mock
        val teamRepository: TeamRepository = mock(TeamRepository::class)

        @Mock
        val selfTeamIdProvider: SelfTeamIdProvider = mock(SelfTeamIdProvider::class)

        val useCase: ApproveLegalHoldRequestUseCase by lazy { ApproveLegalHoldRequestUseCaseImpl(teamRepository, selfTeamIdProvider) }

        fun arrange() = this to useCase

        suspend fun withGetSelfTeamResult(result: Either<CoreFailure, TeamId?>) = apply {
            coEvery {
                selfTeamIdProvider.invoke()
            }.returns(result)
        }

        suspend fun withApproveLegalHoldResult(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                teamRepository.approveLegalHoldRequest(any(), any())
            }.returns(result)
        }
    }
}
