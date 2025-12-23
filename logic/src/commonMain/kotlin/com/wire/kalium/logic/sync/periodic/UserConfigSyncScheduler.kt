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
 * Responsible for [schedulePeriodicUserConfigSync].
 *
 *  This refresh should be done:
 *  - For desktop/web: On launch, when the app gains focus, and every 24 hours that the app has been running
 *  - For mobile: When the app comes to the foreground (iOS, Android), and on Android every 24 hours that the app has been
 *    in the foreground.
 *
 */
internal interface UserConfigSyncScheduler {

    /**
     *  Schedules a periodic execution of [UserConfigSyncWorker] that is responsible for syncing/refreshing user configurations:
     *  - feature configs
     *  - Certificate Revocation List (CRL)
     *  - MLS public keys
     *  - Proteus pre-keys
     *
     *  API version is also part of the user config, but it is updated by [UpdateApiVersionsWorker] and not here.
     *
     *  **When** it's gonna to be executed may vary depending on the platform and/or implementation.
     */
    fun schedulePeriodicUserConfigSync()

    /**
     *  Resets the backoff for the enqueued [UserConfigSyncWorker] if it's already waiting to be retried, so that it can be done instantly.
     */
    fun resetBackoffForPeriodicUserConfigSync()
}
