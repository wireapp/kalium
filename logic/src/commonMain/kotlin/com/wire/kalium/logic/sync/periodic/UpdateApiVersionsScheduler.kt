/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.sync.periodic

/**
 * Responsible for [schedulePeriodicApiVersionUpdate] and [scheduleImmediateApiVersionUpdate].
 *
 *  Clients should retrieve the list of supported version from the backend:
 *  - All clients: When the application starts (doesn’t matter if the user is logged in or not)
 *  - Web: at least once every 24 hours
 *  - Mobile: at least once every 24 hours OR whenever the app comes to the foreground
 *
 */
interface UpdateApiVersionsScheduler {

    /**
     *  Schedules a periodic execution of [UpdateApiVersionsWorker], which checks and tries to determine
     *  the API version to use.
     *
     *  **When** it's gonna to be executed may vary depending on the platform and/or implementation.
     *
     *  One of the criteria in order to attempt sending a message is that there's
     *  an established internet connection. So the scheduler *may* take this into consideration.
     */
    fun schedulePeriodicApiVersionUpdate()

    /**
     *  Schedules an immediate execution of [UpdateApiVersionsWorker], which checks and tries to determine
     *  the API version to use.
     */
    fun scheduleImmediateApiVersionUpdate()

}
