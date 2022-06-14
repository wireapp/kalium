package com.wire.kalium.logic.sync

/**
 * Responsible for [scheduleSlowSync].
 */
interface SlowSyncScheduler {

    /**
     *  Schedules an execution of [SlowSyncWorker].
     */
    fun scheduleSlowSync()
}
