package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import kotlin.reflect.KClass

/**
 * Exposes ways of scheduling and enqueuing work to be done in the background.
 */
expect sealed class WorkScheduler {

    /**
     * Schedules some work to be done in the background, in a "run and forget" way – free from client app observation.
     * On mobile clients for example, aims to start a job that will not be suspended by the user minimizing the app.
     */
    fun enqueueImmediateWork(work: KClass<out DefaultWorker>, name: String)

    class Global : WorkScheduler, UpdateApiVersionsScheduler
    class UserSession: WorkScheduler, MessageSendingScheduler, SlowSyncScheduler {
        val userId: UserId
    }
}
