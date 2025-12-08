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
package com.wire.kalium.logic.featureFlags

/**
 * JVM implementation of LiteSyncConfig.fromEnvironment()
 * Reads the KALIUM_LITESYNC_PRIMARY environment variable
 */
actual fun liteSyncConfigFromEnvironment(): LiteSyncConfig? {
    val primaryAddress = System.getenv(LiteSyncConfig.ENV_LITESYNC_PRIMARY)
    return if (primaryAddress != null && primaryAddress.isNotBlank()) {
        LiteSyncConfig(
            primaryAddress = primaryAddress,
            nodeType = LiteSyncConfig.NodeType.SECONDARY
        )
    } else {
        null
    }
}
