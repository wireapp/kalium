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
import com.wire.kalium.logic.functional.Either
import kotlinx.coroutines.flow.Flow

/**
 * Observes the current user's server configuration.
 * - auth server provider, regular and custom backend
 */
interface ObserveSelfServerConfigUseCase {
    /**
     * @return [ServerConfigResult]
     */
    suspend operator fun invoke(serverLinks: ServerConfig.Links): Either<CoreFailure, Flow<ServerConfigResult>>
}

@Suppress("FunctionNaming")
internal fun ObserveSelfServerConfigUseCase(
    serverConfigRepository: ServerConfigRepository
): ObserveSelfServerConfigUseCase = object : ObserveSelfServerConfigUseCase {
    override suspend operator fun invoke(serverLinks: ServerConfig.Links): Either<CoreFailure, Flow<ServerConfigResult>> {
        // serverConfigRepository.getOrFetchMetadata(serverLinks)
        // fetch api version and resolve if can use the new experience
        // if proxy mod with auth, then old experience as default.
        TODO()
    }
}

sealed class ServerConfigResult {
    data class Success(val canUseNewFlow: Boolean) : ServerConfigResult()
}
