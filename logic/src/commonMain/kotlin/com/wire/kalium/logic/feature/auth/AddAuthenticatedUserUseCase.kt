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

package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onSuccess

/**
 * Adds an authenticated user to the session
 * In case of the new session having a different server configurations, the new session should not be added
 */
class AddAuthenticatedUserUseCase internal constructor(
    private val sessionRepository: SessionRepository,
    private val serverConfigRepository: ServerConfigRepository
) {
    sealed class Result {
        data class Success(val userId: UserId) : Result()
        sealed class Failure : Result() {
            object UserAlreadyExists : Failure()
            data class Generic(val genericFailure: CoreFailure) : Failure()
        }
    }

    suspend operator fun invoke(
        serverConfigId: String,
        ssoId: SsoId?,
        authTokens: AccountTokens,
        proxyCredentials: ProxyCredentials?,
        replace: Boolean = false
    ): Result = sessionRepository.doesValidSessionExist(authTokens.userId).fold(
            {
                Result.Failure.Generic(it)
            }, { doesValidSessionExist ->
                when (doesValidSessionExist) {
                    true -> onUserExist(serverConfigId, ssoId, authTokens, proxyCredentials, replace)
                    false -> storeUser(serverConfigId, ssoId, authTokens, proxyCredentials)
                }
            }
        )

    private suspend fun storeUser(
        serverConfigId: String,
        ssoId: SsoId?,
        accountTokens: AccountTokens,
        proxyCredentials: ProxyCredentials?
    ): Result =
        sessionRepository.storeSession(serverConfigId, ssoId, accountTokens, proxyCredentials)
            .onSuccess {
                sessionRepository.updateCurrentSession(accountTokens.userId)
            }.fold(
                { Result.Failure.Generic(it) },
                { Result.Success(accountTokens.userId) }
            )

    // In case of the new session have a different server configurations the new session should not be added
    private suspend fun onUserExist(
        newServerConfigId: String,
        ssoId: SsoId?,
        newAccountTokens: AccountTokens,
        proxyCredentials: ProxyCredentials?,
        replace: Boolean
    ): Result =
        when (replace) {
            true -> {
                sessionRepository.fullAccountInfo(newAccountTokens.userId).fold(
                    { Result.Failure.Generic(it) },
                    { oldSession ->
                        val newServerConfig =
                            serverConfigRepository.configById(newServerConfigId).fold({ return Result.Failure.Generic(it) }, { it })
                        if (oldSession.serverConfig.links == newServerConfig.links) {
                            storeUser(
                                serverConfigId = newServerConfigId,
                                ssoId = ssoId,
                                accountTokens = newAccountTokens,
                                proxyCredentials = proxyCredentials
                            )
                        } else Result.Failure.UserAlreadyExists
                    }
                )
            }

            false -> Result.Failure.UserAlreadyExists
        }
}
