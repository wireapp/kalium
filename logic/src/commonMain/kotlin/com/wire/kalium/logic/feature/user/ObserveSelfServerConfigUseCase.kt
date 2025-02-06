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

package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Observes the current user's server configuration.
 */
interface ObserveSelfServerConfigUseCase {
    /**
     * @return [ServerConfigResult]
     */
    suspend operator fun invoke(): Either<CoreFailure, Flow<ServerConfigResult>>
}

@Suppress("FunctionNaming")
internal fun ObserveSelfServerConfigUseCase(
    selfUserId: UserId,
    serverConfigRepository: ServerConfigRepository
): ObserveSelfServerConfigUseCase = object : ObserveSelfServerConfigUseCase {
    override suspend operator fun invoke(): Either<CoreFailure, Flow<ServerConfigResult>> =
        serverConfigRepository.observeConfigForUser(selfUserId).map { result ->
            result.map { serverConfig -> serverConfig?.let { ServerConfigResult.Success(it) } ?: ServerConfigResult.Default }
        }
}

sealed class ServerConfigResult {
    /**
     * Default result instead of null, in case the server configuration is not available, equals to null.
     */
    data object Default : ServerConfigResult()

    /**
     * The server config was loaded successfully.
     */
    data class Success(val serverConfig: ServerConfig) : ServerConfigResult()
}
