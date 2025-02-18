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
package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.user.UpdateEmailUseCase.Result
import com.wire.kalium.common.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isInvalidEmail
import com.wire.kalium.network.exceptions.isKeyExists

/**
 * Updates the user's email address.
 * keep in mind that this use case does not update the email immediately, it only sends a request to the backend.
 * the use need to confirm the email change by clicking on the link in an email they receive.
 * @see [UserRepository.updateSelfEmail]
 * @param email The new email address.
 * @return [Result.Success] if the email was updated successfully.
 * @return [Result.Failure.InvalidEmail] if the email is invalid.
 * @return [Result.Failure.EmailAlreadyInUse] if the email is already in use.
 * @return [Result.Failure.GenericFailure] if the email update failed for any other reason.
 */
class UpdateEmailUseCase internal constructor(
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(email: String): Result = accountRepository.updateSelfEmail(email)
        .fold(::onError, ::onSuccess)

    private fun onError(error: NetworkFailure): Result.Failure {
        return if (error is NetworkFailure.ServerMiscommunication && error.kaliumException is KaliumException.InvalidRequestError) {
            val exception = error.kaliumException as KaliumException.InvalidRequestError
            when {
                exception.isKeyExists() -> Result.Failure.EmailAlreadyInUse
                exception.isInvalidEmail() -> Result.Failure.InvalidEmail
                else -> Result.Failure.GenericFailure(error)
            }
        } else {
            Result.Failure.GenericFailure(error)
        }
    }

    private fun onSuccess(isEmailUpdated: Boolean): Result.Success {
        return if (isEmailUpdated) {
            Result.Success.VerificationEmailSent
        } else {
            Result.Success.NoChange
        }
    }

    sealed interface Result {
        sealed interface Success : Result {
            data object VerificationEmailSent : Success
            data object NoChange : Success
        }

        sealed interface Failure : Result {
            data object InvalidEmail : Failure
            data object EmailAlreadyInUse : Failure
            data class GenericFailure(val error: NetworkFailure) : Failure
        }
    }
}
