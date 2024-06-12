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

package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.kmmSettings.KaliumPreferences

interface LastRetrievedNotificationEventStorage {
    /**
     * to save the id of the last event retrieved from the notifications stream
     */
    fun saveEvent(eventId: String)

    /**
     * get the id of the last saved event if one exists
     */
    fun getLastEventId(): String?
}

internal class LastRetrievedNotificationEventStorageImpl internal constructor(
    private val kaliumPreferences: KaliumPreferences
) : LastRetrievedNotificationEventStorage {

    override fun saveEvent(eventId: String) {
        kaliumPreferences.putString(
            LAST_NOTIFICATION_STREAM_EVENT_ID,
            eventId
        )
    }

    override fun getLastEventId(): String? =
        kaliumPreferences.getString(LAST_NOTIFICATION_STREAM_EVENT_ID)

    private companion object {
        const val LAST_NOTIFICATION_STREAM_EVENT_ID = "last_notification_stream_event_id"
    }
}
