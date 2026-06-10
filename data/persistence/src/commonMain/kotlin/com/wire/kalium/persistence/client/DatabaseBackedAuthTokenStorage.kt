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

package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.daokaliumdb.GlobalAuthSessionEntity
import com.wire.kalium.persistence.daokaliumdb.GlobalProxyCredentialsEntity
import com.wire.kalium.persistence.daokaliumdb.GlobalSecretsDAO
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock

internal class DatabaseBackedAuthTokenStorage(
    private val globalSecretsDAO: GlobalSecretsDAO,
    private val clock: Clock = Clock.System
) : AuthTokenStorage {
    override fun addOrReplace(authTokenEntity: AuthTokenEntity, proxyCredentialsEntity: ProxyCredentialsEntity?) {
        runBlocking {
            globalSecretsDAO.upsertAuthSession(authTokenEntity.toGlobalEntity(clock.now().toEpochMilliseconds()))
            proxyCredentialsEntity?.let {
                globalSecretsDAO.upsertProxyCredentials(
                    it.toGlobalEntity(
                        userId = authTokenEntity.userId,
                        updatedAt = clock.now().toEpochMilliseconds()
                    )
                )
            }
        }
    }

    override fun updateToken(
        userId: UserIDEntity,
        accessToken: String,
        tokenType: String,
        refreshToken: String?
    ): AuthTokenEntity = runBlocking {
        val currentToken = globalSecretsDAO.authSession(userId)
            ?: error("No token found for user ${userId.toLogString()}")
        val updatedToken = currentToken.copy(
            accessToken = accessToken,
            refreshToken = refreshToken ?: currentToken.refreshToken,
            tokenType = tokenType,
            updatedAt = clock.now().toEpochMilliseconds()
        )
        globalSecretsDAO.upsertAuthSession(updatedToken)
        updatedToken.toAuthTokenEntity()
    }

    override fun getToken(userId: UserIDEntity): AuthTokenEntity? = runBlocking {
        globalSecretsDAO.authSession(userId)?.toAuthTokenEntity()
    }

    override fun deleteToken(userId: UserIDEntity) {
        runBlocking {
            globalSecretsDAO.deleteAuthSession(userId)
            globalSecretsDAO.deleteProxyCredentials(userId)
        }
    }

    override fun proxyCredentials(userId: UserIDEntity): ProxyCredentialsEntity? = runBlocking {
        globalSecretsDAO.proxyCredentials(userId)?.toProxyCredentialsEntity()
    }

    private fun AuthTokenEntity.toGlobalEntity(updatedAt: Long): GlobalAuthSessionEntity =
        GlobalAuthSessionEntity(
            userId = userId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = tokenType,
            cookieLabel = cookieLabel,
            updatedAt = updatedAt
        )

    private fun GlobalAuthSessionEntity.toAuthTokenEntity(): AuthTokenEntity =
        AuthTokenEntity(
            userId = userId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = tokenType,
            cookieLabel = cookieLabel
        )

    private fun ProxyCredentialsEntity.toGlobalEntity(userId: UserIDEntity, updatedAt: Long): GlobalProxyCredentialsEntity =
        GlobalProxyCredentialsEntity(
            userId = userId,
            username = username,
            password = password,
            updatedAt = updatedAt
        )

    private fun GlobalProxyCredentialsEntity.toProxyCredentialsEntity(): ProxyCredentialsEntity =
        ProxyCredentialsEntity(
            username = username,
            password = password
        )
}
