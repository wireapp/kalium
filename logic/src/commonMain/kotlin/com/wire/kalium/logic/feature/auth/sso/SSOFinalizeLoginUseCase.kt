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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode

sealed class SSOFinalizeLoginResult {
    data class Success(val requestUrl: String) : SSOFinalizeLoginResult()

    sealed class Failure : SSOFinalizeLoginResult() {
        data object InvalidCookie : SSOFinalizeLoginResult.Failure()
        data class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

/**
 * Finalizes a login using SSO
 */
interface SSOFinalizeLoginUseCase {
    /**
     * @param cookie the cookie to use for the login
     * @return the [SSOFinalizeLoginResult] with the request url if successful
     */
    suspend operator fun invoke(cookie: String): SSOFinalizeLoginResult
}

internal class SSOFinalizeLoginUseCaseImpl(
    private val ssoLoginRepository: SSOLoginRepository
) : SSOFinalizeLoginUseCase {

    override suspend fun invoke(cookie: String): SSOFinalizeLoginResult =
        ssoLoginRepository.finalize(cookie).fold({
            if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
                if (it.kaliumException.errorResponse.code == HttpStatusCode.BadRequest.value)
                    return@fold SSOFinalizeLoginResult.Failure.InvalidCookie
            }
            SSOFinalizeLoginResult.Failure.Generic(it)
        }, {
            SSOFinalizeLoginResult.Success(it)
        })
}
