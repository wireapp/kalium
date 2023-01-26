/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.logout

/**
 * Describes a reason that led to a logout.
 */
enum class LogoutReason {
    /**
     * User initiated the logout manually and opted to not delete user data.
     */
    SELF_SOFT_LOGOUT,

    /**
     * User initiated the logout manually and opted to delete user data.
     */

    SELF_HARD_LOGOUT,

    /**
     * User deleted this client from another client.
     */
    REMOVED_CLIENT,

    /**
     * User delete their account.
     */
    DELETED_ACCOUNT,

    /**
     * Session Expired.
     */
    SESSION_EXPIRED;
}
