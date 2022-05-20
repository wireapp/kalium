package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

actual class WorkSchedulerImpl(private val coreLogic: CoreLogic, private val userId: UserId): WorkScheduler {

    override fun enqueueImmediateWork(
        work: KClass<out UserSessionWorker>,
        name: String
    ) {
        GlobalScope.launch {
            val constructor = work.java.getDeclaredConstructor(UserSessionScope::class.java)
            val worker = constructor.newInstance(coreLogic.getSessionScope(userId)) as UserSessionWorker
            worker.doWork()
        }
    }

    override suspend fun scheduleSendingOfPendingMessages() {
        kaliumLogger.w(
            "Scheduling of messages is not supported on JVM. Pending messages won't be scheduled for sending."
        )
    }
}
