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

/**
 * Gets the [ServerConfig.Links] stored locally, using the url as a key.
 */
class GetServerConfigUseCase internal constructor(
    private val customServerConfigRepository: CustomServerConfigRepository
) {
    /**
     * @param url the url to use as a key to get the [ServerConfig.Links]
     * @return the [Result] with the [ServerConfig.Links] if successful, otherwise a mapped failure.
     */
    suspend operator fun invoke(url: String): GetServerConfigResult = customServerConfigRepository.fetchRemoteConfig(url).fold({
        GetServerConfigResult.Failure.Generic(it)
    }, { GetServerConfigResult.Success(it) })
}

sealed class GetServerConfigResult {
    // TODO: change to return the id only so we are now passing the whole config object around in the app
    class Success(val serverConfigLinks: ServerConfig.Links) : GetServerConfigResult()

    sealed class Failure : GetServerConfigResult() {
        data class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
