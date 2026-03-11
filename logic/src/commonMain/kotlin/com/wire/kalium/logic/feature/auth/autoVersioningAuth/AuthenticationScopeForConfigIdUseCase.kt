/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.auth.autoVersioningAuth

import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO

/**
 * Returns an [AuthenticationScope] for a stored server configuration ID,
 * skipping the network round-trip that [AutoVersionAuthScopeUseCase] performs.
 */
public class AuthenticationScopeForConfigIdUseCase internal constructor(
    private val serverConfigurationDAO: ServerConfigurationDAO,
    private val serverConfigMapper: ServerConfigMapper,
    private val authenticationScopeFactory: (ServerConfig) -> AuthenticationScope,
) {
    public operator fun invoke(configId: String): AutoVersionAuthScopeUseCase.Result =
        wrapStorageRequest { serverConfigurationDAO.configById(configId) }
            .fold(
                { AutoVersionAuthScopeUseCase.Result.Failure.Generic(it) },
                { entity ->
                    val serverConfig = serverConfigMapper.fromEntity(entity)
                    AutoVersionAuthScopeUseCase.Result.Success(authenticationScopeFactory(serverConfig))
                }
            )
}
