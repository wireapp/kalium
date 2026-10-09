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
 */
internal fun interface CellsCredentialsProvider {

    /**
     * @return the credentials, or null while the Cells backend URL is not known yet
     * (e.g. right after login, before the feature config is synced).
     */
    suspend fun credentials(): CellsCredentials?
}

/**
 * Temporary solution until we make we way to get the serverUrl and gateway secret.
 *
 * Credentials are cached only once the backend URL is available, so a lookup made before the
 * feature config is synced does not keep the feature broken for the rest of the session.
 */
internal class CellsCredentialsProviderImpl(
    private val getConfiguration: GetWireCellConfigurationUseCase
) : CellsCredentialsProvider {

    private var cachedCredentials: CellsCredentials? = null

    override suspend fun credentials(): CellsCredentials? =
        cachedCredentials ?: getConfiguration()?.backendUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { CellsCredentials(serverUrl = it, gatewaySecret = GATEWAY_SECRET) }
            ?.also { cachedCredentials = it }

    private companion object {
        const val GATEWAY_SECRET = "gatewaysecret"
    }
}
