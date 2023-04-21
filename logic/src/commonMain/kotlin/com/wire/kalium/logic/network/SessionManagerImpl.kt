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

package com.wire.kalium.logic.network

import app.cash.sqldelight.internal.Atomic
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.nullableFold
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.base.model.AccessTokenDTO
import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.base.model.RefreshTokenDTO
import com.wire.kalium.network.api.base.model.SessionDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isUnknownClient
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.tools.ServerConfigDTO
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
    private val userId: QualifiedID,
    private val tokenStorage: AuthTokenStorage,
    private val logout: suspend (LogoutReason) -> Unit,
    private val coroutineContext: CoroutineContext = KaliumDispatcherImpl.default.limitedParallelism(1),
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper(),
    private val serverConfigMapper: ServerConfigMapper = MapperProvider.serverConfigMapper()
) : SessionManager {

    private val session: Atomic<SessionDTO?> = Atomic(null)
    private var serverConfig: Atomic<ServerConfigDTO?> = Atomic(null)

    override suspend fun session(): SessionDTO? = withContext(coroutineContext) {
        session.get() ?: run {
            wrapStorageRequest { tokenStorage.getToken(userId.toDao()) }
                .map { sessionMapper.fromEntityToSessionDTO(it) }
                .onSuccess { session.set(it) }
                .onFailure { kaliumLogger.e("""SESSION MANAGER: 
                    |"error": "missing user session",
                    |"cause": "$it" """.trimMargin()) }
            session.get()
        }
    }

    override fun serverConfig(): ServerConfigDTO = serverConfig.get() ?: run {
        serverConfig.set(sessionRepository.fullAccountInfo(userId)
            .map { serverConfigMapper.toDTO(it.serverConfig) }
            .fold({ error("use serverConfig is missing or an error while reading local storage") }, { it })
        )
        serverConfig.get()!!
    }

    override suspend fun updateLoginSession(newAccessTokenDTO: AccessTokenDTO, newRefreshTokenDTO: RefreshTokenDTO?): SessionDTO? =
        wrapStorageRequest {
            tokenStorage.updateToken(
                userId = userId.toDao(),
                accessToken = newAccessTokenDTO.value,
                tokenType = newAccessTokenDTO.tokenType,
                refreshToken = newRefreshTokenDTO?.value
            )
        }.map {
            sessionMapper.fromEntityToSessionDTO(it)
        }.onSuccess {
            session.set(it)
        }.nullableFold({
            null
        }, {
            it
        })

    override suspend fun updateToken(accessTokenApi: AccessTokenApi, oldAccessToken: String, oldRefreshToken: String): SessionDTO? {
        return withContext(coroutineContext) {
            wrapApiRequest { accessTokenApi.getToken(oldRefreshToken) }.nullableFold({
                when (it) {
                    is NetworkFailure.NoNetworkConnection -> null
                    is NetworkFailure.ProxyError -> null
                    is NetworkFailure.FederatedBackendFailure -> null
                    is NetworkFailure.ServerMiscommunication -> {
                        onServerMissCommunication(it)
                        null
                    }
                }
            }, {
                updateLoginSession(it.first, it.second)
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
