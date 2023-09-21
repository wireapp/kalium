/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.periodic.UpdateApiVersionsWorker
import kotlinx.coroutines.runBlocking

actual class GlobalWorkSchedulerImpl(
    private val coreLogic: CoreLogic
) : GlobalWorkScheduler {
    override fun schedulePeriodicApiVersionUpdate() {
        kaliumLogger.w(
            "Scheduling a periodic execution of checking the API version is not supported on Apple devices."
        )
    }

    override fun scheduleImmediateApiVersionUpdate() {
        runBlocking {
            coreLogic.globalScope {
                UpdateApiVersionsWorker(updateApiVersions).doWork()
            }
        }
    }
}

internal actual class UserSessionWorkSchedulerImpl(
    override val userId: UserId
) : UserSessionWorkScheduler {
    override fun scheduleSendingOfPendingMessages() {
        TODO("Not yet implemented")
    }

    override fun cancelScheduledSendingOfPendingMessages() {
        TODO("Not yet implemented")
    }

}
