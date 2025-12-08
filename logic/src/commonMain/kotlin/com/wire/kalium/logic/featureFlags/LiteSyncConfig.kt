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
 * Configuration for LiteSync database synchronization.
 * When configured, the SQLite database will act as a LiteSync secondary node
 * that synchronizes with a primary node at the specified address.
 *
 * @property primaryAddress The address of the LiteSync primary node (e.g., "tcp://server:1234")
 * @property nodeType The type of node (PRIMARY or SECONDARY)
 * @property enabled Whether LiteSync synchronization is enabled
 */
data class LiteSyncConfig(
    val primaryAddress: String,
    val nodeType: NodeType = NodeType.SECONDARY,
    val enabled: Boolean = true
) {
    /**
     * Node type for LiteSync synchronization topology
     */
    enum class NodeType {
        /** Primary node that accepts connections from secondary nodes */
        PRIMARY,
        /** Secondary node that connects to a primary node */
        SECONDARY
    }

    /**
     * Generates the LiteSync connection string parameters
     * @return URI query parameters for LiteSync configuration
     */
    fun toUriParameters(): String {
        return when (nodeType) {
            NodeType.PRIMARY -> "node=primary&bind=$primaryAddress"
            NodeType.SECONDARY -> "node=secondary&connect=$primaryAddress"
        }
    }

    companion object {
        /**
         * Environment variable name for configuring LiteSync primary address
         */
        const val ENV_LITESYNC_PRIMARY = "KALIUM_LITESYNC_PRIMARY"

        /**
         * Creates a LiteSyncConfig from environment variables if available
         * @return LiteSyncConfig if environment variable is set, null otherwise
         */
        fun fromEnvironment(): LiteSyncConfig? = liteSyncConfigFromEnvironment()
    }
}

/**
 * Platform-specific implementation to read LiteSync configuration from environment
 */
expect fun liteSyncConfigFromEnvironment(): LiteSyncConfig?
