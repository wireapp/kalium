package com.wire.kalium.logic.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthSession
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass


class WrapperWorker(private val innerWorker: UserSessionWorker, appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

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

}

class WrapperWorkerFactory(private val coreLogic: CoreLogic): WorkerFactory() {

    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? {
        if (WrapperWorker::class.java.canonicalName != workerClassName) {
            return null // delegate to default factory
        }

        val userSessionEncoded = workerParameters.inputData.getString(WrapperWorkerFactory.SESSION_KEY)
        val innerWorkerClassName = workerParameters.inputData.getString(WrapperWorkerFactory.WORKER_CLASS_KEY)

        if (userSessionEncoded == null || innerWorkerClassName == null) {
            throw RuntimeException("No session was specified")
        }

        val userSession: AuthSession = Json.decodeFromString(userSessionEncoded)
        val constructor = Class.forName(innerWorkerClassName).getDeclaredConstructor(UserSessionScope::class.java)
        val innerWorker = constructor.newInstance(coreLogic.getSessionScope(userSession))
        return WrapperWorker(innerWorker as UserSessionWorker, appContext, workerParameters)
    }

    companion object {
        const val WORKER_CLASS_KEY = "worker_class"
        const val SESSION_KEY = "session"
    }

}

actual class WorkScheduler(private val context: Context, private val session: AuthSession) {

    actual fun schedule(work: KClass<out UserSessionWorker>, name: String) {
        val inputData = Data.Builder()
            .putString(WrapperWorkerFactory.WORKER_CLASS_KEY, work.java.canonicalName)
            .putString(WrapperWorkerFactory.SESSION_KEY, Json.encodeToString(session))
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
