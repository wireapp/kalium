package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.user.UserId
import kotlin.reflect.KClass

// Mockative can't mock interfaces with KClass<out SuperClass> parameters in functions
class FakeWorkScheduler(override val userId: UserId = UserId("user_id", "domain.de")) : UserSessionWorkScheduler {
    var enqueueImmediateWorkCallCount = 0

    override fun enqueueImmediateWork(work: KClass<out DefaultWorker>, name: String) {
        enqueueImmediateWorkCallCount++
    }

    var scheduleSendingOfPendingMessages = 0
    override fun scheduleSendingOfPendingMessages() {
        scheduleSendingOfPendingMessages++
    }

    var scheduleSlowSync = 0
    override fun scheduleSlowSync() {
        scheduleSlowSync++
    }
}
