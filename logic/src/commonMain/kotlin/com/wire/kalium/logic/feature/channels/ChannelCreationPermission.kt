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
package com.wire.kalium.logic.feature.channels

/**
 * Represents the permission state for channel creation in the system.
 * This sealed interface defines whether channel creation is allowed or forbidden for users.
 */
sealed interface ChannelCreationPermission {
    /**
     * Represents a state where channel creation is not permitted for the user.
     */
    data object Forbidden : ChannelCreationPermission

    /**
     * Represents a state where channel creation is permitted with specific public channel creation rights.
     *
     * @property canSelfUserCreatePublicChannels Indicates whether the current user can create public channels
     */
    data class Allowed(val canSelfUserCreatePublicChannels: Boolean) : ChannelCreationPermission
}
