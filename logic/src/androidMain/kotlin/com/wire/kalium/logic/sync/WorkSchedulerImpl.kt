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
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.periodic.UpdateApiVersionsWorker
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.TimeUnit

private val workerClass = WrapperWorker::class.java

internal actual class GlobalWorkSchedulerImpl(
    private val appContext: Context,
    private val coreLogic: CoreLogic
) : GlobalWorkScheduler {

    override fun schedulePeriodicApiVersionUpdate() {
        val inputData = WrapperWorkerFactory.workData(UpdateApiVersionsWorker::class)

        val connectedConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val scheduledHourOfDayToExecute = TIME_OF_EXECUTION // schedule at 4AM
        val repeatIntervalInHours = REPEAT_INTERVAL // execute every 24 hours
        val localTimeZone = TimeZone.currentSystemDefault()
        val timeNow: Instant = Clock.System.now() // current time
        val timeScheduledToExecute = timeNow.toLocalDateTime(localTimeZone) // time at which the today's execution should take place
            .let { localDateTimeNow ->
                LocalDateTime(
                    localDateTimeNow.year, localDateTimeNow.monthNumber, localDateTimeNow.dayOfMonth,
                    scheduledHourOfDayToExecute, 0, 0, 0
                ).toInstant(localTimeZone)
            }
        val initialDelayMillis = // delay calculated as a difference between now and next scheduled execution
            if (timeScheduledToExecute > timeNow) (timeScheduledToExecute - timeNow).inWholeMilliseconds
            else (timeScheduledToExecute.plus(1, DateTimeUnit.DAY, localTimeZone) - timeNow).inWholeMilliseconds

        val requestPeriodicWork = PeriodicWorkRequest.Builder(workerClass, repeatIntervalInHours.toLong(), TimeUnit.HOURS)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(connectedConstraint)
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            "${UpdateApiVersionsWorker.name}-periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            requestPeriodicWork
        )
    }

    override fun scheduleImmediateApiVersionUpdate() {
        runBlocking {
            coreLogic.globalScope {
                UpdateApiVersionsWorker(updateApiVersions).doWork()
            }
        }
    }

    private companion object {
        const val TIME_OF_EXECUTION = 4
        const val REPEAT_INTERVAL: Long = 24
    }
}

internal actual class UserSessionWorkSchedulerImpl(
    private val appContext: Context,
    override val userId: UserId
) : UserSessionWorkScheduler {

    override fun scheduleSendingOfPendingMessages() {
        val inputData = WrapperWorkerFactory.workData(PendingMessagesSenderWorker::class, userId)

        val connectedConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequest.Builder(workerClass)
            .setConstraints(connectedConstraint)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            WORK_NAME_PREFIX_PER_USER + userId.value,
            ExistingWorkPolicy.APPEND,
            request
        )
    }

    private companion object {
        const val WORK_NAME_PREFIX_PER_USER = "scheduled-message-"
    }
}
