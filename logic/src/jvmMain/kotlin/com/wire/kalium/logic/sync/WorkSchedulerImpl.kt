package com.wire.kalium.logic.sync

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.kaliumLogger

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
    override val userId: UserId,
) : UserSessionWorkScheduler {

    override fun scheduleSendingOfPendingMessages() {
        kaliumLogger.withFeatureId(SYNC).w(
            "Scheduling of messages is not supported on JVM. Pending messages won't be scheduled for sending."
        )
    }
}
