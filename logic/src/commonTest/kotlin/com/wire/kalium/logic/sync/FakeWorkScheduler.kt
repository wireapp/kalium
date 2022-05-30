package com.wire.kalium.logic.sync

import kotlin.reflect.KClass

// Mockative can't mock interfaces with KClass<out SuperClass> parameters in functions
class FakeWorkScheduler : WorkScheduler {
    var scheduleSendingOfPendingMessagesCallCount = 0
    override suspend fun scheduleSendingOfPendingMessages() {
        scheduleSendingOfPendingMessagesCallCount++
    }

    var enqueueImmediateWorkCallCount = 0
    override fun enqueueImmediateWork(work: KClass<out UserSessionWorker>, name: String) {
        enqueueImmediateWorkCallCount++
    }
}
