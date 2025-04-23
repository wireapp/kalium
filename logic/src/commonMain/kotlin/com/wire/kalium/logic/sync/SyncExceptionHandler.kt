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

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.logger.kaliumLogger
import kotlinx.coroutines.CancellationException

internal class SyncExceptionHandler(
    private val onCancellation: suspend () -> Unit,
    private val onFailure: suspend (exception: CoreFailure) -> Unit
) {
    private val logger = kaliumLogger.withFeatureId(SYNC)

    suspend fun handleException(exception: Throwable) {
        when (exception) {
            is CancellationException -> {
                logger.w("Sync job was cancelled", exception)
                onCancellation()
            }

            is KaliumSyncException -> {
                logger.i("SyncException during events processing", exception)
                onFailure(exception.coreFailureCause)
            }

            else -> {
                logger.i("Sync job failed due to unknown reason", exception)
                onFailure(CoreFailure.Unknown(exception))
            }
        }
    }
}
