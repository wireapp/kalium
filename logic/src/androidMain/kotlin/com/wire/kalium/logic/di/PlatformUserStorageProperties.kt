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
import com.wire.kalium.persistence.db.LiteSyncNodeType

/**
 * Configuration for LiteSync database synchronization.
 *
 * @property syncUri The LiteSync server URI (e.g., "tcp://192.168.1.100:1234")
 * @property nodeType The node type: PRIMARY for the main node, SECONDARY for replicas
 * @property onReady Optional callback invoked when the database sync is ready
 * @property onSync Optional callback invoked when a sync event occurs
 */
data class LiteSyncConfiguration(
    val syncUri: String,
    val nodeType: LiteSyncNodeType = LiteSyncNodeType.SECONDARY,
    val onReady: (() -> Unit)? = null,
    val onSync: (() -> Unit)? = null
)

actual class PlatformUserStorageProperties internal constructor(
    val applicationContext: Context,
    internal val securityHelper: SecurityHelper,
    val liteSyncConfiguration: LiteSyncConfiguration? = null
)
