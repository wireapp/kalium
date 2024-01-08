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

package com.wire.kalium.logic.data.sync

/**
 * Represents how CoreLogic should handle the connection
 * to the backend when performing Sync and receiving events.
 * @see SyncState
 */
enum class ConnectionPolicy {
    /**
     * After gathering and processing pending events,
     * a websocket connection will be kept alive,
     * receiving live events.
     *
     *
     * This is the default and most usually needed behaviour
     * when the client app is on the foreground, and it's expected
     * for the application to keep consuming resources.
     *
     * @see DISCONNECT_AFTER_PENDING_EVENTS
     */
    KEEP_ALIVE,

    /**
     * After gathering and processing pending events,
     * the websocket should be dropped, impeding the client
     * from staying online and receiving live events.
     *
     * ### Use-cases
     *
     * Needed, for example, when the application wakes
     * up when receiving a push notification, and wants
     * to free resources after processing notifications.
     *
     * @see KEEP_ALIVE
     */
    DISCONNECT_AFTER_PENDING_EVENTS
}
