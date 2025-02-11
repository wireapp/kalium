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
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.data.sso.SSOUtil
import com.wire.kalium.common.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode

sealed class SSOInitiateLoginResult {
    data class Success(val requestUrl: String) : SSOInitiateLoginResult()

    sealed class Failure : SSOInitiateLoginResult() {
        data object InvalidCodeFormat : Failure()
        data object InvalidCode : Failure()
        data object InvalidRedirect : Failure()
        data class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

data class SSORedirects(val success: String, val error: String) {
    constructor(serverConfigId: String) : this(
        SSOUtil.generateSuccessRedirect(serverConfigId),
        SSOUtil.generateErrorRedirect()
    )
}

/**
 * Initiates a login using SSO
 */
interface SSOInitiateLoginUseCase {
    sealed class Param {
        abstract val ssoCode: String

        data class WithoutRedirect(override val ssoCode: String) : Param()
        data class WithRedirect(override val ssoCode: String) : Param()
    }

    /**
     * @param param the [Param] to use for the login with redirect or not
     * @return the [SSOInitiateLoginResult] with the request url if successful
     */
    suspend operator fun invoke(param: Param): SSOInitiateLoginResult
}

internal class SSOInitiateLoginUseCaseImpl(
    private val ssoLoginRepository: SSOLoginRepository,
    private val validateSSOCodeUseCase: ValidateSSOCodeUseCase,
    private val serverConfig: ServerConfig
) : SSOInitiateLoginUseCase {

    override suspend fun invoke(param: SSOInitiateLoginUseCase.Param): SSOInitiateLoginResult = with(param) {
        val validUuid = validateSSOCodeUseCase(ssoCode).let {
            when (it) {
                is ValidateSSOCodeResult.Valid -> it.uuid
                ValidateSSOCodeResult.Invalid -> return@with SSOInitiateLoginResult.Failure.InvalidCodeFormat
            }
        }
        when (this) {
            is SSOInitiateLoginUseCase.Param.WithoutRedirect -> ssoLoginRepository.initiate(validUuid)
            is SSOInitiateLoginUseCase.Param.WithRedirect -> {
                val redirects = SSORedirects(serverConfig.id)
                ssoLoginRepository.initiate(
                    validUuid, redirects.success, redirects.error
                )
            }
        }.fold({
            if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
                if (it.kaliumException.errorResponse.code == HttpStatusCode.BadRequest.value)
                    return@fold SSOInitiateLoginResult.Failure.InvalidRedirect
                if (it.kaliumException.errorResponse.code == HttpStatusCode.NotFound.value)
                    return@fold SSOInitiateLoginResult.Failure.InvalidCode
            }
            SSOInitiateLoginResult.Failure.Generic(it)
        }, {
            SSOInitiateLoginResult.Success(it)
        })
    }
}
