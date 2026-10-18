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

import com.wire.kalium.common.error.wrapStorageNullableRequest
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.client.AuthTokenStorage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * Iterates over all locally stored server configs and update each api version
 */
public interface UpdateApiVersionsUseCase {
    /** Reports changes to the persisted common API version, rather than other server metadata. */
    public suspend operator fun invoke(): Result

    public enum class Result { Changed, Unchanged, Failure, }
}

internal class UpdateApiVersionsUseCaseImpl internal constructor(
    private val sessionRepository: SessionRepository,
    private val tokenStorage: AuthTokenStorage,
    private val serverConfigRepoProvider: (serverConfig: ServerConfig, proxyCredentials: ProxyCredentials?) -> ServerConfigRepository,
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper()
) : UpdateApiVersionsUseCase {
    override suspend operator fun invoke(): UpdateApiVersionsUseCase.Result = coroutineScope {
        val before = sessionRepository.validSessionsWithServerConfig().getOrElse {
            return@coroutineScope UpdateApiVersionsUseCase.Result.Failure
        }
        before.entries
            .distinctBy { it.value.id }
            .map { (userId, serverConfig) ->
                launch { updateApiForUser(userId, serverConfig) }
            }
            .joinAll()

        val after = sessionRepository.validSessionsWithServerConfig().getOrElse {
            return@coroutineScope UpdateApiVersionsUseCase.Result.Failure
        }
        val apiVersionChanged = after.any { (userId, config) ->
            val previousConfig = before[userId] ?: return@any false
            previousConfig.metaData.commonApiVersion.version != config.metaData.commonApiVersion.version
        }

        if (apiVersionChanged) {
            UpdateApiVersionsUseCase.Result.Changed
        } else {
            UpdateApiVersionsUseCase.Result.Unchanged
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
