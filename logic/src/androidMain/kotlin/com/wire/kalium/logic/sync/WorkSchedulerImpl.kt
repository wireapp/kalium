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

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.periodic.UpdateApiVersionsWorker
import com.wire.kalium.logic.sync.periodic.UserConfigSyncWorker
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

internal actual class GlobalWorkSchedulerImpl(
    private val appContext: Context,
    private val coreLogic: CoreLogic
) : GlobalWorkScheduler {

    override fun schedulePeriodicApiVersionUpdate() {
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            "${UpdateApiVersionsWorker.name}-periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            buildConnectedPeriodicWorkRequest(UpdateApiVersionsWorker::class)
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
    private val appContext: Context,
    private val globalKaliumScope: GlobalKaliumScope,
    actual override val userId: UserId
) : UserSessionWorkScheduler {

    actual override fun scheduleSendingOfPendingMessages() {
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            PendingMessagesSenderWorker.NAME_PREFIX + userId.value,
            ExistingWorkPolicy.APPEND,
            buildConnectedOneTimeWorkRequest(PendingMessagesSenderWorker::class, userId)
        )
    }

    actual override fun cancelScheduledSendingOfPendingMessages() {
        WorkManager.getInstance(appContext).cancelUniqueWork(PendingMessagesSenderWorker.NAME_PREFIX + userId.value)
    }

    actual override fun schedulePeriodicUserConfigSync() {
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UserConfigSyncWorker.NAME + userId.value + "-periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            buildConnectedPeriodicWorkRequest(UserConfigSyncWorker::class, userId)
        )
    }

    actual override fun resetBackoffForPeriodicUserConfigSync() {
        globalKaliumScope.launch {
            WorkManager.getInstance(appContext).getWorkInfosForUniqueWorkFlow(UserConfigSyncWorker.NAME + userId.value + "-periodic")
                .firstOrNull()
                ?.firstOrNull()
                ?.let {
                    if (it.state == androidx.work.WorkInfo.State.ENQUEUED && it.runAttemptCount > 0) {
                        // it means that the worker has been retried at least once, so update to reset the backoff
                        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                            UserConfigSyncWorker.NAME + userId.value + "-periodic",
                            ExistingPeriodicWorkPolicy.UPDATE,
                            buildConnectedPeriodicWorkRequest(UserConfigSyncWorker::class, userId, true)
                        )
                    }
                }
        }
    }
}

private fun buildConnectedPeriodicWorkRequest(
    worker: KClass<out DefaultWorker>,
    userId: UserId? = null,
    resetBackoff: Boolean = false,
): PeriodicWorkRequest {
    val inputData = WrapperWorkerFactory.workData(worker, userId)
    val connectedConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    val localTimeZone = TimeZone.currentSystemDefault()
    val timeNow: Instant = DateTimeUtil.currentInstant() // current time
    val timeScheduledToExecute = timeNow.toLocalDateTime(localTimeZone) // time at which the today's execution should take place
        .let { localDateTimeNow ->
            LocalDateTime(localDateTimeNow.year, localDateTimeNow.monthNumber, localDateTimeNow.dayOfMonth, TIME_OF_EXECUTION, 0, 0, 0)
                .toInstant(localTimeZone)
        }
    val initialDelayMillis = // delay calculated as a difference between now and next scheduled execution
        if (timeScheduledToExecute > timeNow) (timeScheduledToExecute - timeNow).inWholeMilliseconds
        else (timeScheduledToExecute.plus(1, DateTimeUnit.DAY, localTimeZone) - timeNow).inWholeMilliseconds
    return PeriodicWorkRequest.Builder(workerClass, REPEAT_INTERVAL, TimeUnit.HOURS)
        .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
        .setConstraints(connectedConstraint)
        .setInputData(inputData)
        .let {
            if (resetBackoff) it.setNextScheduleTimeOverride(System.currentTimeMillis()) else it.clearNextScheduleTimeOverride()
        }
        .build()
}

private fun buildConnectedOneTimeWorkRequest(
    worker: KClass<out DefaultWorker>,
    userId: UserId? = null
): OneTimeWorkRequest {
    val inputData = WrapperWorkerFactory.workData(worker, userId)
    val connectedConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    return OneTimeWorkRequest.Builder(workerClass)
        .setConstraints(connectedConstraint)
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setInputData(inputData)
        .build()
}

private const val TIME_OF_EXECUTION = 4 // schedule at 4AM
private const val REPEAT_INTERVAL: Long = 24 // execute every 24 hours
private val workerClass = WrapperWorker::class.java
