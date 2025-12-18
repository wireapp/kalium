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
import com.wire.kalium.network.exceptions.isBlackListedEmail
import com.wire.kalium.network.exceptions.isDomainBlockedForRegistration
import com.wire.kalium.network.exceptions.isInvalidEmail
import com.wire.kalium.network.exceptions.isKeyExists

/**
 * Use case to request an activation code for a given email address.
 */
internal class RequestActivationCodeUseCase internal constructor(
    private val registerAccountRepository: RegisterAccountRepository
) {
    /**
     * @param email [String] the registered email address to request an activation code for
     * @return [RequestActivationCodeResult.Success] or [RequestActivationCodeResult.Failure] with the specific error.
     */
    internal suspend operator fun invoke(email: String): RequestActivationCodeResult {

        return registerAccountRepository.requestEmailActivationCode(email)
            .fold({
                if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
                    val error = it.kaliumException as KaliumException.InvalidRequestError
                    when {
                        error.isInvalidEmail() -> RequestActivationCodeResult.Failure.InvalidEmail
                        error.isBlackListedEmail() -> RequestActivationCodeResult.Failure.BlacklistedEmail
                        error.isKeyExists() -> RequestActivationCodeResult.Failure.AlreadyInUse
                        error.isDomainBlockedForRegistration() -> RequestActivationCodeResult.Failure.DomainBlocked
                        else -> RequestActivationCodeResult.Failure.Generic(it)
                    }
                } else RequestActivationCodeResult.Failure.Generic(it)
            }, {
                RequestActivationCodeResult.Success
            })
    }
}

internal sealed class RequestActivationCodeResult {
    internal data object Success : RequestActivationCodeResult()
    internal sealed class Failure : RequestActivationCodeResult() {
        internal data object InvalidEmail : Failure()
        internal data object BlacklistedEmail : Failure()
        internal data object AlreadyInUse : Failure()
        internal data object DomainBlocked : Failure()
        internal data class Generic(val failure: NetworkFailure) : Failure()
    }
}
