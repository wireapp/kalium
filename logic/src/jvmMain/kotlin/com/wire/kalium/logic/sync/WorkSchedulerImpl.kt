package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal actual class GlobalWorkSchedulerImpl(
    private val coreLogic: CoreLogic
) : GlobalWorkScheduler {

    override fun schedulePeriodicApiVersionUpdate() {
        kaliumLogger.w(
            "Scheduling a periodic execution of checking the API version is not supported on JVM."
        )
    }
}

internal actual class UserSessionWorkSchedulerImpl(
    private val coreLogic: CoreLogic,
    override val userId: UserId,
    private val kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : UserSessionWorkScheduler {

    private var slowSyncJob: Job? = null
    private val scope = CoroutineScope(kaliumDispatcher.default.limitedParallelism(1))
    override fun enqueueSlowSyncIfNeeded() {
        kaliumLogger.v("SlowSync: Enqueueing if needed")
        scope.launch {
            val isRunning = slowSyncJob?.isActive ?: false

            kaliumLogger.v("SlowSync: Job is running = $isRunning")
            if (!isRunning) {
                slowSyncJob = launch(kaliumDispatcher.default) {
                    SlowSyncWorker(coreLogic.getSessionScope(userId)).doWork()
                }
            } else {
                kaliumLogger.d("SlowSync not scheduled as it's already running")
            }
        }
    }

    override fun scheduleSendingOfPendingMessages() {
        kaliumLogger.w(
            "Scheduling of messages is not supported on JVM. Pending messages won't be scheduled for sending."
        )
    }
}
