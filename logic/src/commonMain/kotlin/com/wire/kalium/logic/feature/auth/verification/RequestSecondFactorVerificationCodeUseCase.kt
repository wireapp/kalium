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
package com.wire.kalium.logic.feature.auth.verification

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.auth.verification.SecondFactorVerificationRepository
import com.wire.kalium.logic.data.auth.verification.VerifiableAction
import com.wire.kalium.logic.feature.register.RequestActivationCodeUseCase
import com.wire.kalium.common.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isTooManyRequests

/**
 * Sends a verification code to an email.
 *
 * This code might be required when performing actions, such as:
 * - Logging in or registering a new client;
 * - Creating a SCIM token;
 * - Deleting a team.
 * See [VerifiableAction] for more.
 *
 * This is unrelated to [RequestActivationCodeUseCase], which is
 * used when registering a new account.
 *
 * The user must open their email inbox and check the verification code.
 * When invoking UseCases related to the actions mentioned above,
 * the code must be provided in order to get a successful response.
 *
 * ## Important!
 * In order to do not leak valid emails, the backend will return
 * a successful response even if the email is not registered.
 *
 * @see VerifiableAction
 */
class RequestSecondFactorVerificationCodeUseCase(
    private val secondFactorVerificationRepository: SecondFactorVerificationRepository,
) {

    suspend operator fun invoke(
        email: String,
        verifiableAction: VerifiableAction,
    ): Result = secondFactorVerificationRepository.requestVerificationCode(email, verifiableAction).fold({
        if (it is NetworkFailure.ServerMiscommunication
            && it.kaliumException is KaliumException.InvalidRequestError
            && it.kaliumException.isTooManyRequests()
        ) {
            Result.Failure.TooManyRequests
        } else {
            Result.Failure.Generic(it)
        }
    }, {
        Result.Success
    })

    interface Result {
        data object Success : Result

        interface Failure : Result {
            data class Generic(val cause: CoreFailure) : Result

            /**
             * The backend is rejecting this request, as too many were
             * made for this email recently.
             * From a UI point of view, you might be able to proceed and request
             * the 2FA code to the user anyway
             */
            data object TooManyRequests : Result
        }
    }
}
