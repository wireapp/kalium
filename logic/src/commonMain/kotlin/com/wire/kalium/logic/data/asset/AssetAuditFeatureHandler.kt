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
package com.wire.kalium.logic.data.asset

import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.configuration.server.isProductionApi
import com.wire.kalium.logic.data.user.UserId
import io.mockative.Mockable

@Mockable
internal interface AssetAuditFeatureHandler {
    suspend fun isAssetAuditLogEnabled(): Boolean
}

internal class AssetAuditFeatureHandlerImpl(
    private val userId: UserId,
    private val userConfigRepository: UserConfigRepository,
    private val serverConfigRepository: ServerConfigRepository,
) : AssetAuditFeatureHandler {
    override suspend fun isAssetAuditLogEnabled() = serverConfigRepository.configForUser(userId)
        .fold(
            { false },
            { serverConfig ->
                userConfigRepository.isAssetAuditLogEnabled() && serverConfig.isOnPremises()
            }
        )
}

private fun ServerConfig.isOnPremises() = links.isOnPremises && !isProductionApi()
