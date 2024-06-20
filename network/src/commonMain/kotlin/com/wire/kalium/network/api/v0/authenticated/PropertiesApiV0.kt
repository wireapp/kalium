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

package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.properties.PropertiesApi
import com.wire.kalium.network.api.base.authenticated.properties.PropertyKey
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.delete
import io.ktor.client.request.put
import io.ktor.client.request.setBody

internal open class PropertiesApiV0 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient,
) : PropertiesApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    private companion object {
        const val PATH_PROPERTIES = "properties"
    }

    override suspend fun setProperty(propertyKey: PropertyKey, propertyValue: Any): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.put("$PATH_PROPERTIES/${propertyKey.key}") { setBody(propertyValue) }
        }

    override suspend fun deleteProperty(propertyKey: PropertyKey): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.delete("$PATH_PROPERTIES/${propertyKey.key}")
    }

}
