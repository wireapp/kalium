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

package com.wire.kalium.iosapp

import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.networkContainer.UnboundNetworkContainer
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.Url

/**
 * This file exists to ensure the KMP framework is generated.
 * All network functionality is re-exported from the network module.
 */
object KaliumNetworkExports {
    const val VERSION = "1.0.0"
}

/**
 * Helper class to provide Swift-friendly APIs that wrap Ktor types.
 * This avoids the complexity of constructing Ktor Url from Swift.
 */
class VersionApiHelper(private val unboundContainer: UnboundNetworkContainer) {
    /**
     * Fetches API version metadata from the given base URL string.
     * This wraps the VersionApi.fetchApiVersion method to accept a String
     * instead of a Ktor Url, making it easier to call from Swift.
     */
    suspend fun fetchApiVersion(baseUrl: String): NetworkResponse<ServerConfigDTO.MetaData> {
        return unboundContainer.versionApi.fetchApiVersion(Url(baseUrl))
    }
}
