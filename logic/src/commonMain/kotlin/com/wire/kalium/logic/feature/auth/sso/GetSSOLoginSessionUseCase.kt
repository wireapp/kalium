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
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode

sealed class SSOLoginSessionResult {
    data class Success(
        val accountTokens: AccountTokens,
        val ssoId: SsoId?,
        val proxyCredentials: ProxyCredentials?
    ) : SSOLoginSessionResult()

    sealed class Failure : SSOLoginSessionResult() {
        data object InvalidCookie : Failure()
        data class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

/**
 * Obtains a session from the server using the provided cookie
 */
interface GetSSOLoginSessionUseCase {
    /**
     * @param cookie the cookie to use for the login
     * @return the [SSOLoginSessionResult] with tokens and proxy credentials
     */
    suspend operator fun invoke(cookie: String): SSOLoginSessionResult
}

internal class GetSSOLoginSessionUseCaseImpl(
    private val ssoLoginRepository: SSOLoginRepository,
    private val proxyCredentials: ProxyCredentials?,
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : GetSSOLoginSessionUseCase {

    override suspend fun invoke(cookie: String): SSOLoginSessionResult =
        ssoLoginRepository.provideLoginSession(cookie).fold({
            if (it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError) {
                if (it.kaliumException.errorResponse.code == HttpStatusCode.BadRequest.value)
                    return@fold SSOLoginSessionResult.Failure.InvalidCookie
            }
            SSOLoginSessionResult.Failure.Generic(it)
        }, {
            SSOLoginSessionResult.Success(
                accountTokens = sessionMapper.fromSessionDTO(it.sessionDTO),
                ssoId = idMapper.toSsoId(it.userDTO.ssoID),
                proxyCredentials = proxyCredentials
            )
        })

}
