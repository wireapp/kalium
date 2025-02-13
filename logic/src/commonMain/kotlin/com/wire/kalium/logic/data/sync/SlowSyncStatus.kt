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

import com.wire.kalium.common.error.CoreFailure
import kotlin.time.Duration

sealed interface SlowSyncStatus {

    data object Pending : SlowSyncStatus

    data object Complete : SlowSyncStatus

    data class Ongoing(val currentStep: SlowSyncStep) : SlowSyncStatus

    data class Failed(val failure: CoreFailure, val retryDelay: Duration) : SlowSyncStatus
}

enum class SlowSyncStep {
    MIGRATION,
    SELF_USER,
    FEATURE_FLAGS,
    UPDATE_SUPPORTED_PROTOCOLS,
    CONVERSATIONS,
    CONNECTIONS,
    SELF_TEAM,
    CONTACTS,
    JOINING_MLS_CONVERSATIONS,
    RESOLVE_ONE_ON_ONE_PROTOCOLS,
    LEGAL_HOLD
}
