/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

internal interface MeetingOccurrencesSyncScheduler {
    /**
     * Schedules a periodic execution of [MeetingOccurrencesSyncWorker] that is responsible for keeping meeting occurrences up to date.
     * It removes the occurrences that are no longer valid and generates new meeting occurrences for recurring meetings to keep the window
     * of occurrences up to date, so that the user can see them properly in the UI.
     */
    fun schedulePeriodicMeetingOccurrencesSync()
}
