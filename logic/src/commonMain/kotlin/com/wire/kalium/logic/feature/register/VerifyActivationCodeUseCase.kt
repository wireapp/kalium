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

package com.wire.kalium.logic.feature.register

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.common.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isInvalidCode

/**
 * Use case to validate an activation code for a given email address.
 */
class VerifyActivationCodeUseCase internal constructor(
    private val registerAccountRepository: RegisterAccountRepository
) {
    /**
     * @param email [String] the registered email address that the activation code was sent to
     * @param code [String] the activation code to validate
     * @return [VerifyActivationCodeResult.Success] or [VerifyActivationCodeResult.Failure] with the specific error.
     */
    suspend operator fun invoke(email: String, code: String): VerifyActivationCodeResult =
        registerAccountRepository.verifyActivationCode(email, code)
            .fold({
                if (
                    it is NetworkFailure.ServerMiscommunication &&
                    it.kaliumException is KaliumException.InvalidRequestError &&
                    (it.kaliumException as KaliumException.InvalidRequestError).isInvalidCode()
                ) {
                    VerifyActivationCodeResult.Failure.InvalidCode
                } else {
                    VerifyActivationCodeResult.Failure.Generic(it)
                }
            }, {
                VerifyActivationCodeResult.Success
            })

}

sealed class VerifyActivationCodeResult {
    data object Success : VerifyActivationCodeResult()
    sealed class Failure : VerifyActivationCodeResult() {
        data object InvalidCode : Failure()
        data class Generic(val failure: NetworkFailure) : Failure()
    }
}
