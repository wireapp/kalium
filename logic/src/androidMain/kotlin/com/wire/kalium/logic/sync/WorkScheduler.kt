package com.wire.kalium.logic.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.R
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass


class WrapperWorker(private val innerWorker: UserSessionWorker, appContext: Context, params: WorkerParameters) :
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

    //TODO: Add support for customization of foreground info when doing work on Android
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(applicationContext, createNotificationChannel().id)
        } else {
            Notification.Builder(applicationContext)
        }.setContentTitle(NOTIFICATION_TITLE)
            .setSmallIcon(R.mipmap.ic_launcher) //TODO: Customize icons too
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): NotificationChannel {
        //TODO: Internationalis(z)ation. Should come as a side-effect of enabling customization of notifications by consumer apps
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

        val userId = workerParameters.getSerializable<UserId>(WrapperWorkerFactory.USER_ID_KEY)
        val innerWorkerClassName = workerParameters.inputData.getString(WrapperWorkerFactory.WORKER_CLASS_KEY)

        if (userId == null || innerWorkerClassName == null) {
            throw RuntimeException("No user id was specified")
        }
        val constructor = Class.forName(innerWorkerClassName).getDeclaredConstructor(UserSessionScope::class.java)
        val innerWorker = constructor.newInstance(coreLogic.getSessionScope(userId))
        return WrapperWorker(innerWorker as UserSessionWorker, appContext, workerParameters)
    }

    companion object {
        const val WORKER_CLASS_KEY = "worker_class"
        const val USER_ID_KEY = "user-id-worker-param"
    }

}

actual class WorkScheduler(private val context: Context, private val userId: UserId) {

    actual fun schedule(work: KClass<out UserSessionWorker>, name: String) {
        val inputData = Data.Builder()
            .putString(WrapperWorkerFactory.WORKER_CLASS_KEY, work.java.canonicalName)
            .putSerializable(WrapperWorkerFactory.USER_ID_KEY, userId)
            .build()
        val request = OneTimeWorkRequest.Builder(WrapperWorker::class.java)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(inputData).build()

        WorkManager.getInstance(context).beginUniqueWork(
            name,
            ExistingWorkPolicy.REPLACE,
            request
        ).enqueue()
    }
}

private inline fun <reified T> Data.Builder.putSerializable(key: String, value: T) = putString(key, Json.encodeToString(value))
private inline fun <reified T> WorkerParameters.getSerializable(key: String): T? = inputData.getString(key)?.let { Json.decodeFromString(it) }
