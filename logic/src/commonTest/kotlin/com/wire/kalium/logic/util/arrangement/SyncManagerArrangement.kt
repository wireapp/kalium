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
package com.wire.kalium.logic.util.arrangement

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.coEvery
import io.mockative.mock

interface SyncManagerArrangement {
        val syncManager: SyncManager

    suspend fun withWaitUntilLiveOrFailure(result: Either<CoreFailure, Unit>)
}

class SyncManagerArrangementImpl : SyncManagerArrangement {
        override val syncManager: SyncManager = mock(SyncManager::class)
    override suspend fun withWaitUntilLiveOrFailure(result: Either<CoreFailure, Unit>) {
        coEvery {
            syncManager.waitUntilLiveOrFailure()
        }.returns(result)
    }
}
