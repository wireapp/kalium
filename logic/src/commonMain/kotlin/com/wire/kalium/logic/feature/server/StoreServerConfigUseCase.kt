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
import com.wire.kalium.logic.configuration.server.CustomServerConfigRepository
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.functional.fold
import io.ktor.http.URLBuilder

/**
 * Stores the server configuration metadata, like main urls and flags for this server.
 */
fun interface StoreServerConfigUseCase {
    /**
     * @param links the server configuration links to store @see [ServerConfig.Links]
     * @param versionInfo the server configuration version to store @see [ServerConfig.VersionInfo]
     * @return the [StoreServerConfigResult] whether the operation was successful or not and the stored [ServerConfig]
     */
    suspend operator fun invoke(links: ServerConfig.Links, versionInfo: ServerConfig.VersionInfo): StoreServerConfigResult
}

internal class StoreServerConfigUseCaseImpl(
    private val configRepository: CustomServerConfigRepository
) : StoreServerConfigUseCase {

    override suspend fun invoke(links: ServerConfig.Links, versionInfo: ServerConfig.VersionInfo): StoreServerConfigResult {
        val cleanWsLink = URLBuilder(links.webSocket).apply {
            pathSegments = pathSegments.toMutableList().apply {
                if (lastOrNull() == "await") removeLast()
            }
        }.buildString()

        val cleanLinks = links.copy(webSocket = cleanWsLink)
        return configRepository.storeConfig(cleanLinks, versionInfo)
            .fold({ StoreServerConfigResult.Failure.Generic(it) }, { StoreServerConfigResult.Success(it) })
    }
}

sealed class StoreServerConfigResult {
    // TODO: change to return the id only so we are now passing the whole config object around in the app
    class Success(val serverConfig: ServerConfig) : StoreServerConfigResult()

    sealed class Failure : StoreServerConfigResult() {
        data class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
