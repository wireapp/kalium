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
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isAccessDenied
import com.wire.kalium.network.exceptions.isBadRequest
import io.ktor.http.HttpStatusCode

/**
 * Use Case that allows the user to accept a requested legal hold.
 */
interface ApproveLegalHoldRequestUseCase {

    /**
     * Use case [ApproveLegalHoldRequestUseCase] operation
     *
     * @param password password for the user account to confirm the action, can be empty for sso users
     * @return a [ApproveLegalHoldRequestUseCase.Result] indicating the operation result
     */
    suspend operator fun invoke(password: String?): Result

    sealed class Result {
        data object Success : Result()
        sealed class Failure : Result() {
            data class GenericFailure(val coreFailure: CoreFailure) : Failure()
            data object InvalidPassword : Failure()
            data object PasswordRequired : Failure()
        }
    }
}

class ApproveLegalHoldRequestUseCaseImpl internal constructor(
    private val teamRepository: TeamRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider,
) : ApproveLegalHoldRequestUseCase {
    override suspend fun invoke(password: String?): ApproveLegalHoldRequestUseCase.Result {
        return selfTeamIdProvider()
            .flatMap {
                if (it == null) Either.Left(StorageFailure.DataNotFound)
                else Either.Right(it)
            }
            .flatMap { teamId ->
                teamRepository.approveLegalHoldRequest(teamId, password)
            }
            .fold({ handleError(it) }, { ApproveLegalHoldRequestUseCase.Result.Success })
    }

    private fun handleError(failure: CoreFailure): ApproveLegalHoldRequestUseCase.Result.Failure =
        if (failure is NetworkFailure.ServerMiscommunication && failure.kaliumException is KaliumException.InvalidRequestError)
            (failure.kaliumException as KaliumException.InvalidRequestError).let { error ->
                when {
                    error.errorResponse.code == HttpStatusCode.BadRequest.value && error.isBadRequest() ->
                        ApproveLegalHoldRequestUseCase.Result.Failure.InvalidPassword
                     error.errorResponse.code == HttpStatusCode.Forbidden.value && error.isAccessDenied() ->
                        ApproveLegalHoldRequestUseCase.Result.Failure.PasswordRequired
                    else -> ApproveLegalHoldRequestUseCase.Result.Failure.GenericFailure(failure)
                }
            }
        else ApproveLegalHoldRequestUseCase.Result.Failure.GenericFailure(failure)
}
