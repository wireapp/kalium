/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.usernetwork

import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.configuration.server.ApiVersionMapperImpl
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigMapperImpl
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.logic.data.session.toProxyCredentialsDTO
import com.wire.kalium.logic.data.session.toSessionDTO
import com.wire.kalium.logic.data.session.token.AccessTokenRepositoryImpl
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresherImpl
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.session.FailureToRefreshTokenException
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.client.AuthTokenStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Authenticated networking for an already prepared account. The caller owns its account lease. */
public class ExistingAccountSessionManager(
    private val userId: UserId,
    private val clientId: ClientId,
    serverConfig: ServerConfig,
    private val nomadUrl: String?,
    private val tokenStorage: AuthTokenStorage,
) : SessionManager {
    private val configuration = ServerConfigMapperImpl(ApiVersionMapperImpl()).toDTO(serverConfig)
    private val proxy = tokenStorage.proxyCredentials(UserIDEntity(userId.value, userId.domain))?.toProxyCredentialsDTO()
    private val refreshMutex = Mutex()

    init {
        existingSession()
    }

    override suspend fun session(): SessionDTO = existingSession()
    override fun serverConfig(): ServerConfigDTO = configuration
    override fun nomadServiceUrl(): String? = nomadUrl
    override fun proxyCredentials(): ProxyCredentialsDTO? = proxy

    override suspend fun updateToken(accessTokenApi: AccessTokenApi, oldRefreshToken: String?): SessionDTO = refreshMutex.withLock {
        require(!oldRefreshToken.isNullOrBlank()) { "Prepared refresh token is missing" }
        val current = existingSession()
        // Another owned client may have refreshed while this request waited for the mutex.
        if (current.refreshToken != oldRefreshToken) return@withLock current
        AccessTokenRefresherImpl(AccessTokenRepositoryImpl(userId, accessTokenApi, tokenStorage))
            .refreshTokenAndPersistSession(oldRefreshToken, clientId)
            .fold(
                { throw FailureToRefreshTokenException("Prepared account token refresh or persistence failed") },
                {
                    check(it.userId == userId) { "Prepared account token identity changed" }
                    it.toSessionDTO()
                }
            )
    }

    private fun existingSession(): SessionDTO {
        val token = checkNotNull(tokenStorage.getToken(UserIDEntity(userId.value, userId.domain))) {
            "Prepared account token is missing"
        }
        check(token.userId == UserIDEntity(userId.value, userId.domain)) { "Prepared account token identity does not match" }
        check(token.accessToken.isNotBlank() && token.refreshToken.isNotBlank()) { "Prepared account token is empty" }
        return token.toSessionDTO()
    }
}
