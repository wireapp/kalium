package com.wire.kalium.logic.sync

/**
 * Responsible for [schedulePeriodicApiVersionCheck] and [scheduleImmediateApiVersionCheck].
 *
 *  Clients should retrieve the list of supported version from the backend:
 *  - All clients: When the application starts (doesn’t matter if the user is logged in or not)
 *  - Web: at least once every 24 hours
 *  - Mobile: at least once every 24 hours OR whenever the app comes to the foreground
 *
 */
interface ApiVersionCheckScheduler {

    /**
     *  Schedules a periodic execution of [ApiVersionCheckWorker], which checks and tries to determine
     *  the API version to use.
     *
     *  **When** it's gonna to be executed may vary depending on the platform and/or implementation.
     *
     *  One of the criteria in order to attempt sending a message is that there's
     *  an established internet connection. So the scheduler *may* take this into consideration.
     */
    fun schedulePeriodicApiVersionCheck()

    /**
     *  Schedules an immediate one time execution of [ApiVersionCheckWorker], which checks and tries to determine
     *  the API version to use.
     *
     *  **When** it's gonna to be executed may vary depending on the platform and/or implementation.
     *
     *  One of the criteria in order to attempt sending a message is that there's
     *  an established internet connection. So the scheduler *may* take this into consideration.
     */
    fun scheduleImmediateApiVersionCheck()
}
