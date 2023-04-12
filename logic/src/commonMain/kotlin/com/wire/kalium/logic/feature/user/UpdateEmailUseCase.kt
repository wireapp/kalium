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
package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isInvalidEmail
import com.wire.kalium.network.exceptions.isKeyExists

class UpdateEmailUseCase internal constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(email: String): Result = userRepository.updateSelfEmail(email)
        .fold(::onError) { Result.Success }


    private fun onError(error: NetworkFailure): Result.Failure {
        return if(error is NetworkFailure.ServerMiscommunication && error.kaliumException is KaliumException.InvalidRequestError) {
            when {
                error.kaliumException.isKeyExists() -> Result.Failure.EmailAlreadyInUse
                error.kaliumException.isInvalidEmail() -> Result.Failure.InvalidEmail
                else -> Result.Failure.GenericFailure(error)
            }
        } else {
            Result.Failure.GenericFailure(error)
        }
    }

    sealed interface Result {
        object Success : Result

        sealed interface Failure : Result {
            object InvalidEmail : Failure
            object EmailAlreadyInUse : Failure
            data class GenericFailure(val error: NetworkFailure) : Failure
        }
    }

}
