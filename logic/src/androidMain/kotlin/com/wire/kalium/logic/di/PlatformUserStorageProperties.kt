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

package com.wire.kalium.logic.di

import android.content.Context
import com.wire.kalium.logic.util.SecurityHelper
import com.wire.kalium.persistence.db.DatabaseStorageMode
import com.wire.kalium.persistence.db.LiteSyncNodeType

private const val DEFAULT_LITESYNC_URI = "tcp://localhost:1234"

/**
 * Default LiteSync storage mode used when no explicit storage mode is specified.
 */
val DefaultLiteSyncStorageMode = DatabaseStorageMode.LiteSync(
    syncUri = DEFAULT_LITESYNC_URI,
    nodeType = LiteSyncNodeType.SECONDARY
)

actual class PlatformUserStorageProperties internal constructor(
    val applicationContext: Context,
    internal val securityHelper: SecurityHelper,
    /**
     * The storage mode for the user database.
     * Defaults to [DefaultLiteSyncStorageMode] (LiteSync with localhost and SECONDARY node).
     *
     * Can be overridden with:
     * - [DatabaseStorageMode.Encrypted] for SQLCipher encryption
     * - [DatabaseStorageMode.Unencrypted] for standard SQLite
     * - [DatabaseStorageMode.LiteSync] with custom configuration
     */
    val storageMode: DatabaseStorageMode = DefaultLiteSyncStorageMode
)
