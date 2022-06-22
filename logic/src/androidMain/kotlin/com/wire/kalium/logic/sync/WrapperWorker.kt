package com.wire.kalium.logic.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.R
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.kaliumLogger
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass
import androidx.work.ListenableWorker.Result as AndroidResult
import com.wire.kalium.logic.sync.Result as KaliumResult

class WrapperWorker(private val innerWorker: DefaultWorker, appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): AndroidResult = when (innerWorker.doWork()) {
        is KaliumResult.Failure -> AndroidResult.success()
        is KaliumResult.Retry -> AndroidResult.failure()
        is KaliumResult.Success -> AndroidResult.retry()
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
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
        val worker = coreLogic.globalScope {
            UpdateApiVersionsWorker(updateApiVersions)
        }
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

        // TODO: delete not used anymore
        internal const val SERVER_CONFIG_ID_KEY = "server-config-id-worker-param"

        fun workData(work: KClass<out DefaultWorker>, userId: UserId? = null) = Data.Builder()
            .putString(WORKER_CLASS_KEY, work.java.canonicalName)
            .apply { userId?.let { putSerializable(USER_ID_KEY, it) } }
            .build()
    }
}

private inline fun <reified T> Data.Builder.putSerializable(key: String, value: T) = putString(key, Json.encodeToString(value))
private inline fun <reified T> WorkerParameters.getSerializable(key: String): T? =
    inputData.getString(key)?.let { Json.decodeFromString(it) }
