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
package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.feature.auth.sso.FetchSSOSettingsUseCase.Result
import com.wire.kalium.common.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode

/**
 * Fetches the SSO settings from the server.
 * @return [Result] with the default SSO code or [CoreFailure].
 */
class FetchSSOSettingsUseCase internal constructor(
    private val ssoLoginRepository: SSOLoginRepository
) {

    suspend operator fun invoke(): Result = ssoLoginRepository.settings()
        .fold({
            if (it is NetworkFailure.ServerMiscommunication &&
                it.kaliumException is KaliumException.InvalidRequestError &&
                (it.kaliumException as KaliumException.InvalidRequestError).errorResponse.code == HttpStatusCode.NotFound.value
            ) {
                Result.Success(null)
            } else {
                Result.Failure(it)
            }
        }, { Result.Success(it.defaultCode) })

    sealed interface Result {
        data class Success(val defaultSSOCode: String?) : Result
        data class Failure(val coreFailure: CoreFailure) : Result
    }
}
