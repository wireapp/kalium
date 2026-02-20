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
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.session.DoesValidSessionExistResult
import com.wire.kalium.logic.sync.periodic.UpdateApiVersionsWorker
import com.wire.kalium.logic.sync.periodic.UserConfigSyncWorker
import com.wire.kalium.logic.sync.receiver.asset.AudioNormalizedLoudnessWorker
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass
import androidx.work.ListenableWorker.Result as AndroidResult
import com.wire.kalium.logic.sync.Result as KaliumResult

public class WrapperWorker internal constructor(
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
            .setContentTitle(NOTIFICATION_TITLE)
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

public class WrapperWorkerFactory(
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
        val workerType = workerParameters.inputData.getString(WORKER_TYPE_KEY)
        val innerWorkerClassName = workerParameters.inputData.getString(WORKER_CLASS_KEY)
            ?: throw IllegalArgumentException("No worker class name specified")

        kaliumLogger.v("WrapperWorkerFactory, creating worker for class name: $innerWorkerClassName")
        val resolvedType = resolveWorkerType(workerType, innerWorkerClassName)
        val worker = runCatching {
            when (resolvedType) {
                WORKER_TYPE_PENDING_MESSAGES -> withSessionScope(userId) { it.pendingMessagesSenderWorker }
                WORKER_TYPE_USER_CONFIG_SYNC -> withSessionScope(userId) { it.userConfigSyncWorker }
                WORKER_TYPE_UPDATE_API_VERSIONS -> coreLogic.getGlobalScope().updateApiVersionsWorker
                WORKER_TYPE_AUDIO_NORMALIZED_LOUDNESS -> createAudioNormalizedLoudnessWorker(userId, workerParameters)
                else -> instantiateWorker(innerWorkerClassName)
            }
        }.getOrElse { error ->
            kaliumLogger.e("Unable to create worker for class: $innerWorkerClassName", error)
            null
        } ?: MissingWorker(innerWorkerClassName)
        return WrapperWorker(worker, appContext, workerParameters, foregroundNotificationDetailsProvider)
    }

    private fun resolveWorkerType(workerType: String?, innerWorkerClassName: String): String? {
        if (workerType != null) return workerType
        return when {
            innerWorkerClassName.matchesWorkerClass(
                PendingMessagesSenderWorker::class.java.canonicalName,
                LEGACY_PENDING_MESSAGES_WORKER_CLASS_NAME
            ) -> WORKER_TYPE_PENDING_MESSAGES

            innerWorkerClassName.matchesWorkerClass(
                UserConfigSyncWorker::class.java.canonicalName,
                LEGACY_USER_CONFIG_SYNC_WORKER_CLASS_NAME
            ) -> WORKER_TYPE_USER_CONFIG_SYNC

            innerWorkerClassName.matchesWorkerClass(
                UpdateApiVersionsWorker::class.java.canonicalName,
                LEGACY_UPDATE_API_VERSIONS_WORKER_CLASS_NAME
            ) -> WORKER_TYPE_UPDATE_API_VERSIONS

            innerWorkerClassName.matchesWorkerClass(
                AudioNormalizedLoudnessWorker::class.java.canonicalName,
                LEGACY_AUDIO_NORMALIZED_LOUDNESS_WORKER_CLASS_NAME
            ) -> WORKER_TYPE_AUDIO_NORMALIZED_LOUDNESS

            else -> null
        }
    }

    private fun createAudioNormalizedLoudnessWorker(
        userId: UserId?,
        workerParameters: WorkerParameters
    ): DefaultWorker? = withSessionScope(userId) { sessionScope ->
        val conversationId: ConversationId? = workerParameters.getSerializable(CONVERSATION_ID_KEY)
        val messageId: String? = workerParameters.inputData.getString(MESSAGE_ID_KEY)
        if (conversationId == null || messageId == null) {
            throw IllegalArgumentException("Missing parameters for ${AudioNormalizedLoudnessWorker.NAME}")
        } else {
            sessionScope.buildAudioNormalizedLoudnessWorker(conversationId, messageId)
        }
    }

    private fun instantiateWorker(innerWorkerClassName: String): DefaultWorker {
        kaliumLogger.d("No specialized constructor found for class $innerWorkerClassName. Default constructor will be used")
        return runCatching {
            Class.forName(innerWorkerClassName).getDeclaredConstructor().newInstance() as DefaultWorker
        }.getOrElse { error ->
            kaliumLogger.e("Unable to instantiate worker: $innerWorkerClassName", error)
            MissingWorker(innerWorkerClassName)
        }
    }

    private fun withSessionScope(userId: UserId?, action: (UserSessionScope) -> DefaultWorker): DefaultWorker? {
        require(userId != null) { "No user id specified" }
        val doesValidSessionExist = runCatching {
            runBlocking {
                coreLogic.globalScope {
                    doesValidSessionExist(userId).let { it is DoesValidSessionExistResult.Success && it.doesValidSessionExist }
                }
            }
        }.getOrElse { error ->
            kaliumLogger.e("Unable to validate session for worker user: $userId", error)
            false
        }
        return if (!doesValidSessionExist) {
            null
        } else {
            runCatching {
                action(coreLogic.getSessionScope(userId))
            }.getOrElse { error ->
                kaliumLogger.e("Unable to create session scope for worker user: $userId", error)
                null
            }
        }
    }

    internal companion object {
        private const val WORKER_CLASS_KEY = "worker_class"
        private const val WORKER_TYPE_KEY = "worker_type"
        internal const val USER_ID_KEY = "user-id-worker-param"
        internal const val CONVERSATION_ID_KEY: String = "conversation-id-param"
        internal const val MESSAGE_ID_KEY: String = "message-id-param"

        private const val WORKER_TYPE_PENDING_MESSAGES = "pending_messages"
        private const val WORKER_TYPE_USER_CONFIG_SYNC = "user_config_sync"
        private const val WORKER_TYPE_UPDATE_API_VERSIONS = "update_api_versions"
        private const val WORKER_TYPE_AUDIO_NORMALIZED_LOUDNESS = "audio_normalized_loudness"

        // Keep compatibility with tasks enqueued before obfuscation/minification changes.
        // See: docs/minification-workmanager-compat.md
        private const val LEGACY_PENDING_MESSAGES_WORKER_CLASS_NAME =
            "com.wire.kalium.logic.sync.PendingMessagesSenderWorker"
        private const val LEGACY_USER_CONFIG_SYNC_WORKER_CLASS_NAME =
            "com.wire.kalium.logic.sync.periodic.UserConfigSyncWorker"
        private const val LEGACY_UPDATE_API_VERSIONS_WORKER_CLASS_NAME =
            "com.wire.kalium.logic.sync.periodic.UpdateApiVersionsWorker"
        private const val LEGACY_AUDIO_NORMALIZED_LOUDNESS_WORKER_CLASS_NAME =
            "com.wire.kalium.logic.sync.receiver.asset.AudioNormalizedLoudnessWorker"

        fun workData(
            work: KClass<out DefaultWorker>,
            userId: UserId? = null,
            conversationId: ConversationId? = null,
            messageId: String? = null
        ) = Data.Builder()
            .putString(WORKER_CLASS_KEY, work.java.canonicalName)
            .putString(WORKER_TYPE_KEY, resolveWorkerType(work))
            .apply {
                userId?.let { putSerializable(USER_ID_KEY, it) }
                conversationId?.let { putSerializable(CONVERSATION_ID_KEY, it) }
                messageId?.let { putString(MESSAGE_ID_KEY, it) }
            }
            .build()

        private fun resolveWorkerType(work: KClass<out DefaultWorker>): String? = when (work) {
            PendingMessagesSenderWorker::class -> WORKER_TYPE_PENDING_MESSAGES
            UserConfigSyncWorker::class -> WORKER_TYPE_USER_CONFIG_SYNC
            UpdateApiVersionsWorker::class -> WORKER_TYPE_UPDATE_API_VERSIONS
            AudioNormalizedLoudnessWorker::class -> WORKER_TYPE_AUDIO_NORMALIZED_LOUDNESS
            else -> null
        }
    }
}

private class MissingWorker(private val workerClassName: String) : DefaultWorker {
    override suspend fun doWork(): KaliumResult {
        kaliumLogger.e("Skipping unknown worker class: $workerClassName")
        return KaliumResult.Failure
    }
}

private fun String.matchesWorkerClass(currentName: String?, legacyName: String): Boolean =
    this == currentName || this == legacyName

private inline fun <reified T> Data.Builder.putSerializable(key: String, value: T) = putString(key, Json.encodeToString(value))
private inline fun <reified T> WorkerParameters.getSerializable(key: String): T? =
    inputData.getString(key)?.let { Json.decodeFromString(it) }
