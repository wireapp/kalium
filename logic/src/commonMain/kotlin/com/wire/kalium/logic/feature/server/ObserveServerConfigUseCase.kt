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

package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import kotlinx.coroutines.flow.Flow

/**
 * Observes for changes and returns the list of [ServerConfig] stored locally.
 */
class ObserveServerConfigUseCase internal constructor(
    private val serverConfigRepository: ServerConfigRepository
) {
    sealed class Result {
        data class Success(val value: Flow<List<ServerConfig>>) : Result()
        sealed class Failure : Result() {
            data class Generic(val genericFailure: CoreFailure) : Failure()
        }
    }

    /**
     * @return the [Result] with the [Flow] list of [ServerConfig] if successful, otherwise a mapped failure.
     */
    suspend operator fun invoke(): Result {
        serverConfigRepository.configList().map { configList ->
            configList.isNullOrEmpty()
        }.onSuccess { isEmpty ->
            if (isEmpty) {
                // TODO: store all of the configs from the build json file
                ServerConfig.DEFAULT.also { config ->
                    // TODO: what do do if one of the insert failed
                    serverConfigRepository.fetchApiVersionAndStore(config).onFailure {
                        return handleError(it)
                    }
                }
            }
        }

        return serverConfigRepository.configFlow().fold({
            Result.Failure.Generic(it)
        }, {
            Result.Success(it)
        })
    }

    private fun handleError(coreFailure: CoreFailure): Result.Failure {
        return Result.Failure.Generic(coreFailure)
    }
}
