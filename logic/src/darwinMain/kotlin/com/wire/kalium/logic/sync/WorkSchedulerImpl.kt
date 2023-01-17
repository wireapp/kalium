package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.periodic.UpdateApiVersionsWorker
import kotlinx.coroutines.runBlocking

internal actual class GlobalWorkSchedulerImpl(
    private val coreLogic: CoreLogic
) : GlobalWorkScheduler {
    override fun schedulePeriodicApiVersionUpdate() {
        kaliumLogger.w(
            "Scheduling a periodic execution of checking the API version is not supported on Darwin."
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

}
