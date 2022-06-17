package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

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

class LastRetrievedNotificationEventStorageImpl(
    private val kaliumPreferences: KaliumPreferences
    ): LastRetrievedNotificationEventStorage {

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
