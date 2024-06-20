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

package com.wire.kalium.logic.network

import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresherFactory
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.nullableFold
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isUnknownClient
import com.wire.kalium.network.session.FailureToRefreshTokenException
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.util.KaliumDispatcherImpl
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList")
class SessionManagerImpl internal constructor(
    private val sessionRepository: SessionRepository,
    private val accessTokenRefresherFactory: AccessTokenRefresherFactory,
    private val userId: QualifiedID,
    private val tokenStorage: AuthTokenStorage,
    private val logout: suspend (LogoutReason) -> Unit,
    private val coroutineContext: CoroutineContext = KaliumDispatcherImpl.default.limitedParallelism(1),
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper(),
    private val serverConfigMapper: ServerConfigMapper = MapperProvider.serverConfigMapper()
) : SessionManager {

    private var serverConfig: ServerConfigDTO? = null

    override suspend fun session(): SessionDTO? = withContext(coroutineContext) {
        wrapStorageRequest { tokenStorage.getToken(userId.toDao()) }
            .map { sessionMapper.fromEntityToSessionDTO(it) }
            .nullableFold(
                {
                    logout(LogoutReason.SESSION_EXPIRED)
                    null
                }, { session ->
                    session
                }
            )
    }

    override fun serverConfig(): ServerConfigDTO = serverConfig ?: run {
        sessionRepository.fullAccountInfo(userId)
            .map { serverConfigMapper.toDTO(it.serverConfig) }
            .onSuccess { serverConfig = it }
            .fold({ error("use serverConfig is missing or an error while reading local storage") }, { it })
        serverConfig!!
    }

    override suspend fun updateToken(
        accessTokenApi: AccessTokenApi,
        oldAccessToken: String,
        oldRefreshToken: String
    ): SessionDTO {
        val refresher = accessTokenRefresherFactory.create(accessTokenApi)
        return withContext(coroutineContext) {
            refresher.refreshTokenAndPersistSession(oldRefreshToken)
                .map { refreshResult ->
                    SessionDTO(
                        userId = userId.toApi(),
                        tokenType = refreshResult.accessToken.tokenType,
                        accessToken = refreshResult.accessToken.value,
                        refreshToken = refreshResult.refreshToken.value,
                        cookieLabel = refreshResult.cookieLabel
                    )
                }.fold({
                    if (it is NetworkFailure.ServerMiscommunication) {
                        onServerMissCommunication(it)
                    }
                    val message = "Failure during auth token refresh. " +
                            "A network request is failing because of this. " +
                            "Future requests should reattempt to refresh the token. Failure='$it'"
                    kaliumLogger.w(message)
                    throw FailureToRefreshTokenException(message)
                }, {
                    it
                })
        }
    }

    private suspend fun onServerMissCommunication(serverMiscommunication: NetworkFailure.ServerMiscommunication) {
        with(serverMiscommunication.kaliumException) {
            if (this is KaliumException.InvalidRequestError) {
                if (this.errorResponse.code == HttpStatusCode.Forbidden.value)
                    onSessionExpired()
                if (this.isUnknownClient())
                    onClientRemoved()
            }
        }
    }

    private suspend fun onSessionExpired() {
        kaliumLogger.d("SESSION MANAGER: onSessionExpired is called for user ${userId.value.obfuscateId()}")
        logout(LogoutReason.SESSION_EXPIRED)
    }

    private suspend fun onClientRemoved() {
        kaliumLogger.d("SESSION MANAGER: onClientRemoved is called for user ${userId.value.obfuscateId()}")
        logout(LogoutReason.REMOVED_CLIENT)
    }

    override fun proxyCredentials(): ProxyCredentialsDTO? =
        wrapStorageRequest { tokenStorage.proxyCredentials(userId.toDao()) }.nullableFold({
            null
        }, {
            sessionMapper.fromEntityToProxyCredentialsDTO(it)
        })
}
