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

package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.fold

/**
 * This use case is responsible for retrieving the current user's server configuration.
 */
class SelfServerConfigUseCase internal constructor(
    private val selfUserId: UserId,
    private val serverConfigRepository: ServerConfigRepository
) {
    /**
     * @return [ServerConfig] or [CoreFailure]
     */
    suspend operator fun invoke(): Result =
        serverConfigRepository.configForUser(selfUserId).fold({
            Result.Failure(it)
        }, {
            Result.Success(it)
        })

    sealed class Result {
        // TODO: rename serverLinks to serverConfig
        data class Success(val serverLinks: ServerConfig) : Result()
        data class Failure(val cause: CoreFailure) : Result()
    }
}
