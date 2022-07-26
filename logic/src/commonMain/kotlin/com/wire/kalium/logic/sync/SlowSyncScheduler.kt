package com.wire.kalium.logic.sync

/**
 * Responsible for [enqueueSlowSyncIfNeeded].
 */
interface SlowSyncScheduler {

    /**
     * Schedules Slow Sync to be done in the background, in a "run and forget" way – free from client app observation.
     * If already scheduled or running, no need to re-run it.
     * On mobile clients for example, aims to start a job that will not be suspended by the user minimizing the app.
     */
    fun enqueueSlowSyncIfNeeded()
}
