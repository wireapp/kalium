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

import com.wire.kalium.common.functional.intervalFlow
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.feature.UserSessionScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.days

internal actual class WorkSchedulerProviderImpl : WorkSchedulerProvider {
    actual override fun globalWorkScheduler(scope: GlobalKaliumScope): GlobalWorkScheduler = GlobalWorkSchedulerImpl(scope)
    actual override fun userSessionWorkScheduler(scope: UserSessionScope): UserSessionWorkScheduler = UserSessionWorkSchedulerImpl(scope)
}

internal actual class GlobalWorkSchedulerImpl(
    actual override val scope: GlobalKaliumScope,
) : GlobalWorkScheduler {

    actual override fun schedulePeriodicApiVersionUpdate() {
        scope.launch {
            intervalFlow(1.days.inWholeMilliseconds).collect {
                scope.updateApiVersionsWorker.doWork()
            }
        }
    }

    actual override fun scheduleImmediateApiVersionUpdate() {
        runBlocking {
            scope.updateApiVersionsWorker.doWork()
        }
    }
}

internal actual open class UserSessionWorkSchedulerImpl(
    actual override val scope: UserSessionScope,
) : UserSessionWorkScheduler {

    actual override fun scheduleSendingOfPendingMessages() {
        kaliumLogger.withFeatureId(SYNC).w(
            "Scheduling of messages is not supported on JVM. Pending messages won't be scheduled for sending."
        )
    }

    actual override fun cancelScheduledSendingOfPendingMessages() {
        kaliumLogger.withFeatureId(SYNC).w(
            "Cancelling scheduling of messages is not supported on JVM. Pending messages won't be scheduled for sending."
        )
    }

    actual override fun schedulePeriodicUserConfigSync() {
        scope.launch {
            intervalFlow(1.days.inWholeMilliseconds).collect {
                scope.userConfigSyncWorker.doWork()
            }
        }
    }

    actual override fun resetBackoffForPeriodicUserConfigSync() {
        kaliumLogger.withFeatureId(SYNC).w(
            "Resetting backoff for user config sync is not supported on JVM as it doesn't have any backoff mechanism."
        )
    }
}
