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

/**
 * Provides credentials for Cells API based on current environment.
 * Temporary solution until we start using Wire API for cells.
 */
internal object CellsCredentialsProvider {
    internal fun getCredentials(userId: String, serverConfig: ServerConfigDTO) =
        when {
            serverConfig.links.api.endsWith("imai.wire.link") ->
                CellsCredentials(
                    serverUrl = "https://service.zeta.pydiocells.com",
                    accessToken = "fybwjf05cs4bex54ufvmnktttttov1pw:$userId",
                    gatewaySecret = "gatewaysecret",
                )

            serverConfig.links.api.endsWith("fulu.wire.link") -> CellsCredentials(
                serverUrl = "https://shares.fulu.wire.link",
                accessToken = "rnFZ9M3L27j2rxR3h8mvNs3X4ZKk2427ZH5gBnTt:$userId",
                gatewaySecret = "gatewaysecret",
            )

            else -> CellsCredentials("", "", "")
        }
}
