package com.wire.kalium.logic.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.R
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import com.wire.kalium.logic.kaliumLogger
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.lang.IllegalArgumentException
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

class WrapperWorker(private val innerWorker: DefaultWorker, appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return when (innerWorker.doWork()) {
            is com.wire.kalium.logic.sync.Result.Success -> {
                return Result.success()
            }
            is com.wire.kalium.logic.sync.Result.Failure -> {
                return Result.failure()
            }
            is com.wire.kalium.logic.sync.Result.Retry -> {
                return Result.retry()
            }
        }
    }

    //TODO(ui-polishing): Add support for customization of foreground info when doing work on Android
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(applicationContext, createNotificationChannel().id)
        } else {
            Notification.Builder(applicationContext)
        }.setContentTitle(NOTIFICATION_TITLE)
            .setSmallIcon(R.mipmap.ic_launcher) //TODO(ui-polishing): Customize icons too
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): NotificationChannel {
        //TODO(ui-polishing): Internationalis(z)ation. Should come as a
        //                    side-effect of enabling customization of notifications by consumer apps
        val name = "Wire Sync"
        val descriptionText = "Updating conversations and contact information"
        val importance = NotificationManager.IMPORTANCE_NONE
        val channel = NotificationChannel(CHANNEL_ID, name, importance)
        channel.description = descriptionText
        val notificationManager = applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        return channel
    }

    private companion object {
        const val NOTIFICATION_TITLE = "Wire is updating"
        const val NOTIFICATION_ID = -778899
        const val CHANNEL_ID = "kaliumWorker"
    }
}

class WrapperWorkerFactory(private val coreLogic: CoreLogic) : WorkerFactory() {

    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? {
        if (WrapperWorker::class.java.canonicalName != workerClassName) {
            return null // delegate to default factory
        }

        val userId = workerParameters.getSerializable<UserId>(USER_ID_KEY)
        val innerWorkerClassName = workerParameters.inputData.getString(WORKER_CLASS_KEY)
            ?: throw IllegalArgumentException("No worker class name specified")

        kaliumLogger.v("WrapperWorkerFactory, creating worker for class name: $innerWorkerClassName")
        return when (innerWorkerClassName) {
            PendingMessagesSenderWorker::class.java.canonicalName -> {
                require(userId != null) { "No user id specified" }
                createPendingMessageSenderWorker(workerParameters, userId, appContext)
            }
            SlowSyncWorker::class.java.canonicalName -> {
                require(userId != null) { "No user id specified" }
                createSlowSyncWorker(workerParameters, userId, appContext)
            }
            UpdateApiVersionsWorker::class.java.canonicalName -> {
                createApiVersionCheckWorker(workerParameters, appContext)
            }
            else -> {
                kaliumLogger.d("No specialized constructor found for class $innerWorkerClassName. Default constructor will be used")
                createDefaultWorker(innerWorkerClassName, appContext, workerParameters)
            }
        }
    }

    private fun createApiVersionCheckWorker(workerParameters: WorkerParameters, appContext: Context): WrapperWorker {
        val worker = UpdateApiVersionsWorker(coreLogic.apiVersionCheckManager, coreLogic.getAuthenticationScope().updateApiVersions)
        return WrapperWorker(worker, appContext, workerParameters)
    }

    private fun createSlowSyncWorker(workerParameters: WorkerParameters, userId: UserId, appContext: Context): WrapperWorker {
        val worker = SlowSyncWorker(coreLogic.getSessionScope(userId))
        return WrapperWorker(worker, appContext, workerParameters)
    }

    private fun createPendingMessageSenderWorker(workerParameters: WorkerParameters, userId: UserId, appContext: Context): WrapperWorker {
        val userScope = coreLogic.getSessionScope(userId)
        val worker = PendingMessagesSenderWorker(
            userScope.messages.messageRepository,
            userScope.messages.messageSender,
            userId
        )
        return WrapperWorker(worker, appContext, workerParameters)
    }

    private fun createDefaultWorker(
        innerWorkerClassName: String,
        appContext: Context,
        workerParameters: WorkerParameters
    ): WrapperWorker {
        val constructor = Class.forName(innerWorkerClassName).getDeclaredConstructor()
        val innerWorker = constructor.newInstance()
        return WrapperWorker(innerWorker as DefaultWorker, appContext, workerParameters)
    }

    internal companion object {
        private const val WORKER_CLASS_KEY = "worker_class"
        internal const val USER_ID_KEY = "user-id-worker-param"
        internal const val SERVER_CONFIG_ID_KEY = "server-config-id-worker-param"

        fun workData(work: KClass<out DefaultWorker>, userId: UserId? = null) = Data.Builder()
            .putString(WORKER_CLASS_KEY, work.java.canonicalName)
            .apply { if (userId != null) putSerializable(USER_ID_KEY, userId) }
            .build()
    }
}

actual sealed class WorkScheduler(private val appContext: Context) {
    internal val workerClass = WrapperWorker::class.java

    /**
     * Schedules some work to be done in the background, in a "run and forget" way – free from client app observation.
     * On mobile clients for example, aims to start a job that will not be suspended by the user minimizing the app.
     */
    actual fun enqueueImmediateWork(work: KClass<out DefaultWorker>, name: String) {
        val inputData = WrapperWorkerFactory.workData(work, if (this is UserSession) this.userId else null)

        val request = OneTimeWorkRequest.Builder(workerClass)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(inputData).build()

        WorkManager.getInstance(appContext).beginUniqueWork(
            name,
            ExistingWorkPolicy.REPLACE,
            request
        ).enqueue()
    }

    actual class Global(
        private val appContext: Context
    ) : WorkScheduler(appContext), UpdateApiVersionsScheduler {

        override fun schedulePeriodicApiVersionUpdate() {
            val inputData = WrapperWorkerFactory.workData(UpdateApiVersionsWorker::class)

            val connectedConstraint = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val scheduledHourOfDayToExecute = 4 // schedule at 4AM
            val repeatIntervalInHours = 24 // execute every 24 hours
            val localTimeZone = TimeZone.currentSystemDefault()
            val timeNow: Instant = Clock.System.now() // current time
            val timeScheduledToExecute = timeNow.toLocalDateTime(localTimeZone) // time at which the today's execution should take place
                .let { localDateTimeNow -> LocalDateTime(
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
            val inputData = WrapperWorkerFactory.workData(UpdateApiVersionsWorker::class)

            val connectedConstraint = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val requestOneTimeWork = OneTimeWorkRequest.Builder(workerClass)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(connectedConstraint)
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(appContext).enqueueUniqueWork(
                "${UpdateApiVersionsWorker.name}-immediate",
                ExistingWorkPolicy.KEEP,
                requestOneTimeWork
            )
        }
    }

    actual class UserSession(
        private val appContext: Context,
        actual val userId: UserId
    ) : WorkScheduler(appContext), MessageSendingScheduler, SlowSyncScheduler {

        override fun scheduleSlowSync() {
            enqueueImmediateWork(SlowSyncWorker::class, SlowSyncWorker.name)
        }

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
}

private inline fun <reified T> Data.Builder.putSerializable(key: String, value: T) = putString(key, Json.encodeToString(value))
private inline fun <reified T> WorkerParameters.getSerializable(key: String): T? =
    inputData.getString(key)?.let { Json.decodeFromString(it) }
