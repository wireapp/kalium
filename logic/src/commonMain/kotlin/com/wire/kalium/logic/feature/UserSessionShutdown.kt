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
package com.wire.kalium.logic.feature

import com.wire.kalium.cells.CellsScope
import com.wire.kalium.logic.di.KaliumConsumerRegistry
import com.wire.kalium.logic.feature.auth.LogoutUseCaseImpl
import com.wire.kalium.logic.feature.call.CallManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeout

/** Account work drains before its existing platform owner may close resources. */
internal class UserSessionShutdown(
    private val consumers: KaliumConsumerRegistry,
    private val sessionJob: Job,
    private val cells: Lazy<CellsScope>,
    private val calling: Lazy<CallManager>,
) {
    suspend fun stop() {
        consumers.invalidate()
        withTimeout(LogoutUseCaseImpl.SCOPE_DRAIN_TIMEOUT_MS) {
            if (cells.isInitialized()) {
                cells.value.uploadCoordinator.cancelAll()
                cells.value.uploadCoordinator.dismissAll()
                cells.value.cancel()
            }
            // Preserve the existing owner cancellation order. A slow consumer must not consume the
            // timeout before session workers have even received their cancellation request.
            sessionJob.cancelAndJoin()
            if (cells.isInitialized()) cells.value.coroutineContext.job.join()
            if (calling.isInitialized()) calling.value.cancelJobs()
            consumers.invalidateAndJoin()
        }
    }
}
