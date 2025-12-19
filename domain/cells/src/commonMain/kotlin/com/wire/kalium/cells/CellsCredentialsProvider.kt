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
package com.wire.kalium.cells

import com.wire.kalium.cells.domain.model.CellsCredentials
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.persistence.dao.unread.UserConfigDAO

/**
 * Provides credentials for Cells API based on current environment.
 * Temporary solution until we make we way to get the serverUrl and gateway secret.
 */
internal class CellsCredentialsProvider(
    private val userConfigDAO: UserConfigDAO
) {
    internal suspend fun getCredentials() = CellsCredentials(
        // Url is required and supposed to be configured if Cells Feature is enabled
        // Setting empty URL will fail all network requests "turning off" the feature
        serverUrl = userConfigDAO.getWireCellsConfig()?.backendUrl ?: "",
        gatewaySecret = "gatewaysecret"
    )

    internal fun getCredentials(serverConfig: ServerConfigDTO) =
        when {
            serverConfig.links.api.endsWith("imai.wire.link") ->
                CellsCredentials(
                    serverUrl = "https://cells.imai.wire.link",
                    gatewaySecret = "gatewaysecret",
                )

            serverConfig.links.api.endsWith("fulu.wire.link") -> CellsCredentials(
                serverUrl = "https://cells.fulu.wire.link",
                gatewaySecret = "gatewaysecret",
            )

            serverConfig.links.api.endsWith("staging-nginz-https.zinfra.io") -> CellsCredentials(
                serverUrl = "https://cells.staging.zinfra.io",
                gatewaySecret = "gatewaysecret",
            )

            serverConfig.links.api.endsWith("rod-nginz-https.wire.com") -> CellsCredentials(
                serverUrl = "https://cells-beta.wire.com",
                gatewaySecret = "gatewaysecret",
            )

            else -> CellsCredentials(serverConfig.links.api, "gatewaysecret")
        }
}
