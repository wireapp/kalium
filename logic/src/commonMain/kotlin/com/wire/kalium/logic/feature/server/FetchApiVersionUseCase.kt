/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.failure.ServerConfigFailure
import com.wire.kalium.logic.functional.fold

/**
 * Fetches the server api version, for the given server backend.
 */
interface FetchApiVersionUseCase {
    /**
     * @param serverLinks the server backend links to fetch the api version from
     * @return the [FetchApiVersionResult] the server configuration version if successful, otherwise a mapped failure.
     */
    suspend operator fun invoke(serverLinks: ServerConfig.Links): FetchApiVersionResult
}

class FetchApiVersionUseCaseImpl internal constructor(
    private val configRepository: ServerConfigRepository
) : FetchApiVersionUseCase {
    override suspend operator fun invoke(serverLinks: ServerConfig.Links): FetchApiVersionResult =
        configRepository.fetchApiVersionAndStore(serverLinks)
            .fold(
                { handleError(it) },
                { FetchApiVersionResult.Success(it) }
            )

    private fun handleError(coreFailure: CoreFailure): FetchApiVersionResult.Failure =
        when (coreFailure) {
            is ServerConfigFailure.NewServerVersion -> FetchApiVersionResult.Failure.TooNewVersion
            is ServerConfigFailure.UnknownServerVersion -> FetchApiVersionResult.Failure.UnknownServerVersion
            else -> FetchApiVersionResult.Failure.UnknownServerVersion
        }
}

sealed class FetchApiVersionResult {
    class Success(val serverConfig: ServerConfig) : FetchApiVersionResult()

    sealed class Failure : FetchApiVersionResult() {
        data object UnknownServerVersion : Failure()
        data object TooNewVersion : Failure()
        data class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
