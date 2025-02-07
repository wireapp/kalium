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

package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.mapToRightOr
import com.wire.kalium.logic.functional.right
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Observes the [LoginContext] that the current server supports.
 */
interface ObserveLoginContextUseCase {
    /**
     * @return [LoginContext]
     */
    suspend operator fun invoke(serverLinks: ServerConfig.Links): Flow<LoginContext>
}

@Suppress("FunctionNaming")
internal fun ObserveLoginContextUseCase(
    serverConfigRepository: ServerConfigRepository
): ObserveLoginContextUseCase = object : ObserveLoginContextUseCase {
    override suspend operator fun invoke(serverLinks: ServerConfig.Links): Flow<LoginContext> {
        return serverConfigRepository.observeServerConfigByLinks(serverLinks).map {
            it.flatMap { serverConfig ->
                if (serverConfig.metaData.commonApiVersion.version >= MIN_API_VERSION_FOR_ENTERPRISE_LOGIN
                    && serverLinks.apiProxy == null
                ) {
                    LoginContext.EnterpriseLogin.right()
                } else {
                    LoginContext.FallbackLogin.right()
                }
            }
        }.mapToRightOr(LoginContext.FallbackLogin)
    }
}

sealed interface LoginContext {
    /**
     * The server supports enterprise login experience.
     */
    data object EnterpriseLogin : LoginContext

    /**
     * The server does not support or in case of Proxy used we fallback to current login, for now both flows are supported.
     */
    data object FallbackLogin : LoginContext
}

private const val MIN_API_VERSION_FOR_ENTERPRISE_LOGIN = 8
