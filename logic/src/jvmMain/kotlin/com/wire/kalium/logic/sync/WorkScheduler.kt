package com.wire.kalium.logic.sync

import kotlin.reflect.KClass

actual class WorkScheduler {

    actual fun schedule(
        work: KClass<out UserSessionWorker>,
        name: String,
        sessionId: String
    ) {
        val worker = work.java.getDeclaredConstructor().newInstance()
        worker.doWork()
    }

}
