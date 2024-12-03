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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly")

package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapStorageNullableRequest
import com.wire.kalium.persistence.client.AuthTokenStorage
import io.ktor.util.collections.ConcurrentSet
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * Iterates over all locally stored server configs and update each api version
 */
interface UpdateApiVersionsUseCase {
    suspend operator fun invoke()
}

class UpdateApiVersionsUseCaseImpl internal constructor(
    private val sessionRepository: SessionRepository,
    private val tokenStorage: AuthTokenStorage,
    private val serverConfigRepoProvider: (serverConfig: ServerConfig, proxyCredentials: ProxyCredentials?) -> ServerConfigRepository,
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper()
) : UpdateApiVersionsUseCase {
    override suspend operator fun invoke() {
        coroutineScope {
            val updatedServerId = ConcurrentSet<String>()
            sessionRepository.validSessionsWithServerConfig().getOrElse {
                return@coroutineScope
            }.map { (userId, serverConfig) ->
                launch {
                    if (updatedServerId.contains(serverConfig.id)) {
                        return@launch
                    }
                    updatedServerId.add(serverConfig.id)
                    updateApiForUser(userId, serverConfig)
                }
            }.joinAll()
        }
    }

    private suspend fun updateApiForUser(userId: UserId, serverConfig: ServerConfig) {
        val proxyCredentials: ProxyCredentials? = if (serverConfig.links.apiProxy?.needsAuthentication == true) {
            wrapStorageNullableRequest {
                tokenStorage.proxyCredentials(userId.toDao())
            }.map {
                it?.let { sessionMapper.formEntityToProxyModel(it) }
            }.getOrElse {
                kaliumLogger.d("No proxy credentials found for user ${userId.toLogString()}}")
                return
            }
        } else {
            null
        }
        serverConfigRepoProvider(serverConfig, proxyCredentials).updateConfigMetaData(serverConfig)
    }
}
