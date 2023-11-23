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
package com.wire.kalium.logic.feature.legalhold

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isAccessDenied
import com.wire.kalium.network.exceptions.isBadRequest
import io.ktor.http.HttpStatusCode

/**
 * Use Case that allows the user to accept a requested legal hold.
 */
interface ApproveLegalHoldUseCase {

    /**
     * Use case [ApproveLegalHoldUseCase] operation
     *
     * @param password password for the user account to confirm the action, can be empty for sso users
     * @return a [ApproveLegalHoldUseCase.Result] indicating the operation result
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

class ApproveLegalHoldUseCaseImpl internal constructor(
    private val teamRepository: TeamRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider,
) : ApproveLegalHoldUseCase {
    override suspend fun invoke(password: String?): ApproveLegalHoldUseCase.Result {
        return selfTeamIdProvider()
            .flatMap {
                if (it == null) Either.Left(StorageFailure.DataNotFound)
                else Either.Right(it)
            }
            .flatMap { teamId ->
                teamRepository.approveLegalHold(teamId, password)
            }
            .fold({ handleError(it) }, { ApproveLegalHoldUseCase.Result.Success })
    }

    private fun handleError(failure: CoreFailure): ApproveLegalHoldUseCase.Result.Failure =
        if (failure is NetworkFailure.ServerMiscommunication && failure.kaliumException is KaliumException.InvalidRequestError)
            failure.kaliumException.let { error: KaliumException.InvalidRequestError ->
                when {
                    error.errorResponse.code == HttpStatusCode.BadRequest.value && error.isBadRequest() ->
                        ApproveLegalHoldUseCase.Result.Failure.InvalidPassword
                     error.errorResponse.code == HttpStatusCode.Forbidden.value && error.isAccessDenied() ->
                        ApproveLegalHoldUseCase.Result.Failure.PasswordRequired
                    else -> ApproveLegalHoldUseCase.Result.Failure.GenericFailure(failure)
                }
            }
        else ApproveLegalHoldUseCase.Result.Failure.GenericFailure(failure)
}
