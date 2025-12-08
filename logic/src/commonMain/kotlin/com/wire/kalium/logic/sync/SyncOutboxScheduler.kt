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

package com.wire.kalium.logic.sync

/**
 * Scheduler interface for sync outbox operations.
 * Manages periodic and immediate execution of sync outbox worker.
 */
interface SyncOutboxScheduler {
    /**
     * Schedule periodic sync outbox processing.
     * Runs every 15 minutes with 5-minute flex interval.
     * Requires network connectivity.
     */
    fun schedulePeriodicSyncOutboxProcessing()

    /**
     * Schedule immediate sync outbox processing.
     * Used when pending operations are detected at startup
     * or after enabling sync.
     */
    fun scheduleImmediateSyncOutboxProcessing()

    /**
     * Cancel all scheduled sync outbox processing.
     */
    fun cancelScheduledSyncOutboxProcessing()
}
