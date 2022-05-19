package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass


@OptIn(DelicateCoroutinesApi::class)
actual sealed class WorkSchedulerImpl : WorkScheduler {

    override fun enqueueImmediateWork(work: KClass<out DefaultWorker>, name: String) {
        GlobalScope.launch {
            val constructor = work.java.getDeclaredConstructor()
            val worker = constructor.newInstance() as DefaultWorker
            worker.doWork()
        }
    }

    actual class Global(
        private val coreLogic: CoreLogic
    ) : WorkSchedulerImpl(), GlobalWorkScheduler {

        override fun schedulePeriodicApiVersionUpdate() {
            kaliumLogger.w(
                "Scheduling a periodic execution of checking the API version is not supported on JVM."
            )
        }
    }

    actual class UserSession(
        private val coreLogic: CoreLogic,
        override val userId: UserId
    ) : WorkSchedulerImpl(), UserSessionWorkScheduler {

        override fun scheduleSlowSync() {
            GlobalScope.launch {
                SlowSyncWorker(coreLogic.getSessionScope(userId)).doWork()
            }
        }

        override fun scheduleSendingOfPendingMessages() {
            kaliumLogger.w(
                "Scheduling of messages is not supported on JVM. Pending messages won't be scheduled for sending."
            )
        }
    }
}
