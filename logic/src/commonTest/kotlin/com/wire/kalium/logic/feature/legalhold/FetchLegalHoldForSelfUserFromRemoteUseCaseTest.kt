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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.LegalHoldStatus
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.utils.io.errors.IOException
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FetchLegalHoldForSelfUserFromRemoteUseCaseTest {

    @Test
    fun givenFetchLegalHoldParams_whenFetching_thenTheRepositoryShouldBeCalledWithCorrectParameters() = runTest {
        // given
        val selfTeamId = TeamId(TestTeam.TEAM.id)
        val (arrangement, useCase) = Arrangement()
            .withGetSelfTeamResult(Either.Right(selfTeamId))
            .withFetchLegalHoldStatusResult(Either.Right(LegalHoldStatus.ENABLED))
            .arrange()
        // when
        useCase.invoke()
        // then
        coVerify {
            arrangement.teamRepository.fetchLegalHoldStatus(eq(selfTeamId))
        }.wasInvoked(once)
    }

    @Test
    fun givenASuccess_whenFetching_thenSuccessShouldBeReturned() = runTest {
        // given
        val status = LegalHoldStatus.ENABLED
        val selfTeamId = TeamId(TestTeam.TEAM.id)
        val (_, useCase) = Arrangement()
            .withGetSelfTeamResult(Either.Right(selfTeamId))
            .withFetchLegalHoldStatusResult(Either.Right(status))
            .arrange()
        // when
        val result = useCase()
        // then
        result.shouldSucceed {
            assertEquals(status, it)
        }
    }

    @Test
    fun givenSelfUserIsNotInATeam_whenFetching_thenNoConsentResultShouldBeReturned() = runTest {
        // given
        val status = LegalHoldStatus.NO_CONSENT
        val (_, useCase) = Arrangement()
            .withGetSelfTeamResult(Either.Right(null))
            .arrange()
        // when
        val result = useCase.invoke()
        // then
        result.shouldSucceed {
            assertEquals(status, it)
        }
    }

    @Test
    fun givenGetSelfTeamIdFailure_whenFetching_thenDataNotFoundErrorShouldBeReturned() = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withGetSelfTeamResult(Either.Left(StorageFailure.DataNotFound))
            .arrange()
        // when
        val result = useCase.invoke()
        // then
        result.shouldFail {
            assertEquals(StorageFailure.DataNotFound, it)
        }
    }

    @Test
    fun givenAGenericError_whenFetching_thenGenericErrorShouldBeReturned() = runTest {
        // given
        val selfTeamId = TeamId(TestTeam.TEAM.id)
        val failure = NetworkFailure.ServerMiscommunication(KaliumException.GenericError(IOException("no internet")))
        val (_, useCase) = Arrangement()
            .withGetSelfTeamResult(Either.Right(selfTeamId))
            .withFetchLegalHoldStatusResult(Either.Left(failure))
            .arrange()
        // when
        val result = useCase()
        // then
        result.shouldFail {
            assertEquals(failure, it)
        }
    }

    private class Arrangement {

        val teamRepository: TeamRepository = mock(TeamRepository::class)
        val selfTeamIdProvider: SelfTeamIdProvider = mock(SelfTeamIdProvider::class)
        val useCase: FetchLegalHoldForSelfUserFromRemoteUseCase by lazy {
            FetchLegalHoldForSelfUserFromRemoteUseCaseImpl(teamRepository, selfTeamIdProvider)
        }

        fun arrange() = this to useCase

        suspend fun withGetSelfTeamResult(result: Either<CoreFailure, TeamId?>) = apply {
            coEvery {
                selfTeamIdProvider.invoke()
            }.returns(result)
        }

        suspend fun withFetchLegalHoldStatusResult(result: Either<CoreFailure, LegalHoldStatus>) = apply {
            coEvery {
                teamRepository.fetchLegalHoldStatus(any())
            }.returns(result)
        }
    }
}
