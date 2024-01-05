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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isBlackListedEmail
import com.wire.kalium.network.exceptions.isDomainBlockedForRegistration
import com.wire.kalium.network.exceptions.isInvalidEmail
import com.wire.kalium.network.exceptions.isKeyExists

/**
 * Use case to request an activation code for a given email address.
 */
class RequestActivationCodeUseCase internal constructor(
    private val registerAccountRepository: RegisterAccountRepository
) {
    /**
     * @param email [String] the registered email address to request an activation code for
     * @return [RequestActivationCodeResult.Success] or [RequestActivationCodeResult.Failure] with the specific error.
     */
    suspend operator fun invoke(email: String): RequestActivationCodeResult {

        return registerAccountRepository.requestEmailActivationCode(email)
            .fold({
                if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError)
                    when {
                        it.kaliumException.isInvalidEmail() -> RequestActivationCodeResult.Failure.InvalidEmail
                        it.kaliumException.isBlackListedEmail() -> RequestActivationCodeResult.Failure.BlacklistedEmail
                        it.kaliumException.isKeyExists() -> RequestActivationCodeResult.Failure.AlreadyInUse
                        it.kaliumException.isDomainBlockedForRegistration() -> RequestActivationCodeResult.Failure.DomainBlocked
                        else -> RequestActivationCodeResult.Failure.Generic(it)
                    }
                else RequestActivationCodeResult.Failure.Generic(it)
            }, {
                RequestActivationCodeResult.Success
            })
    }
}

sealed class RequestActivationCodeResult {
    data object Success : RequestActivationCodeResult()
    sealed class Failure : RequestActivationCodeResult() {
        data object InvalidEmail : Failure()
        data object BlacklistedEmail : Failure()
        data object AlreadyInUse : Failure()
        data object DomainBlocked : Failure()
        data class Generic(val failure: NetworkFailure) : Failure()
    }
}
