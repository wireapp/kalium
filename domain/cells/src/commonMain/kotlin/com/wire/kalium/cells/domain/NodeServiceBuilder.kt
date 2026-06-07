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
package com.wire.kalium.cells.domain

import com.wire.kalium.cells.sdk.kmp.api.NodeServiceApi
import io.ktor.client.HttpClient

internal class CellsNotConfiguredException :
    IllegalStateException("Cells feature config has not been synced yet; backend URL is unavailable")

internal object NodeServiceBuilder {

    private const val API_VERSION = "v2"

    fun build(serverUrl: String, httpClient: HttpClient): NodeServiceApi {
        if (serverUrl.isBlank()) throw CellsNotConfiguredException()
        return NodeServiceApi(
            baseUrl = "$serverUrl/$API_VERSION",
            httpClient = httpClient
        )
    }
}
