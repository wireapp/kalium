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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.session.DoesValidSessionExistResult
import com.wire.kalium.logic.sync.periodic.UpdateApiVersionsWorker
import com.wire.kalium.logic.sync.periodic.UserConfigSyncWorker
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass
import androidx.work.ListenableWorker.Result as AndroidResult
import com.wire.kalium.logic.sync.Result as KaliumResult

class WrapperWorker(
    private val innerWorker: DefaultWorker,
    appContext: Context,
    params: WorkerParameters,
    private val foregroundNotificationDetailsProvider: ForegroundNotificationDetailsProvider
) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): AndroidResult = when (innerWorker.doWork()) {
        is KaliumResult.Failure -> AndroidResult.failure()
        is KaliumResult.Retry -> AndroidResult.retry()
        is KaliumResult.Success -> AndroidResult.success()
    }

    // TODO(ui-polishing): Add support for customization of foreground info when doing work on Android
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = Notification.Builder(applicationContext, createNotificationChannel().id)
            .setSmallIcon(foregroundNotificationDetailsProvider.getSmallIconResId())
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(): NotificationChannel {
        // TODO(ui-polishing): Internationalis(z)ation. Should come as a
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

class WrapperWorkerFactory(
    private val coreLogic: CoreLogic,
    private val foregroundNotificationDetailsProvider: ForegroundNotificationDetailsProvider
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        if (WrapperWorker::class.java.canonicalName != workerClassName) {
            return null // delegate to default factory
        }

        val userId = workerParameters.getSerializable<UserId>(USER_ID_KEY)
        val innerWorkerClassName = workerParameters.inputData.getString(WORKER_CLASS_KEY)
            ?: throw IllegalArgumentException("No worker class name specified")

        kaliumLogger.v("WrapperWorkerFactory, creating worker for class name: $innerWorkerClassName")
        return when (innerWorkerClassName) {
            PendingMessagesSenderWorker::class.java.canonicalName -> withSessionScope(userId) { it.pendingMessagesSenderWorker }

            UserConfigSyncWorker::class.java.canonicalName -> withSessionScope(userId) { it.userConfigSyncWorker }

            UpdateApiVersionsWorker::class.java.canonicalName -> coreLogic.getGlobalScope().updateApiVersionsWorker

            else -> {
                kaliumLogger.d("No specialized constructor found for class $innerWorkerClassName. Default constructor will be used")
                Class.forName(innerWorkerClassName).getDeclaredConstructor().newInstance() as DefaultWorker
            }
        }?.let { worker ->
            WrapperWorker(worker, appContext, workerParameters, foregroundNotificationDetailsProvider)
        }
    }

    private fun withSessionScope(userId: UserId?, action: (UserSessionScope) -> DefaultWorker): DefaultWorker? {
        require(userId != null) { "No user id specified" }
        return runBlocking {
            coreLogic.globalScope {
                doesValidSessionExist(userId).let { it is DoesValidSessionExistResult.Success && it.doesValidSessionExist }
            }
        }.let { doesValidSessionExist ->
            if (doesValidSessionExist) action(coreLogic.getSessionScope(userId)) else null
        }
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
