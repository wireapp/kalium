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

package com.wire.kalium.persistence.dao.sync

import io.mockative.Mockable
import kotlinx.datetime.Instant

/**
 * Data Access Object for managing sync state configuration.
 * Stores key-value pairs for sync settings like enabled status and batch size.
 */
@Mockable
interface SyncStateDAO {

    /**
     * Inserts or updates a state configuration value.
     * @param key Configuration key (e.g., "sync_enabled", "batch_size")
     * @param value Configuration value as string
     * @param updatedAt Timestamp of the update
     */
    suspend fun upsertState(key: String, value: String, updatedAt: Instant)

    /**
     * Retrieves a state configuration value by key.
     * @param key Configuration key to lookup
     * @return Configuration value, or null if key doesn't exist
     */
    suspend fun selectState(key: String): String?
}
