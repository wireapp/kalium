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
import com.wire.kalium.cells.domain.usecase.GetWireCellConfigurationUseCase

/**
 * Provides credentials for Cells API based on current environment.
 *
 * Returns `null` when the cells backend URL has not yet been provisioned (e.g. on first
 * install before the `cellsInternal` feature config has been synced from the server).
 * Callers MUST treat `null` as "feature not configured yet" and avoid building any HTTP
 * client with a blank base URL — otherwise requests resolve against the auth host instead
 * of the cells host.
 */
internal class CellsCredentialsProvider(
    private val getConfiguration: GetWireCellConfigurationUseCase
) {
    internal suspend fun getCredentials(): CellsCredentials? {
        val backendUrl = getConfiguration()?.backendUrl
        if (backendUrl.isNullOrBlank()) return null
        return CellsCredentials(
            serverUrl = backendUrl,
            gatewaySecret = "gatewaysecret"
        )
    }
}
