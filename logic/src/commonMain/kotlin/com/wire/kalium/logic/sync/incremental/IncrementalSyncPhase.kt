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
package com.wire.kalium.logic.sync.incremental

/**
 * Incremental sync can be divided into two phases
 */
sealed class IncrementalSyncPhase {
    /**
     * This means in the old or new system getting pending events while the client was offline.
     */
    data object CatchingUp : IncrementalSyncPhase()

    /**
     * This means in the old or new system all pending events were fetched and stored locally and the client is ready to process them.
     */
    data object ReadyToProcess : IncrementalSyncPhase()
}
