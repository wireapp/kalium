package com.wire.kalium.persistence.event

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

interface EventInfoStorage {
    var lastProcessedId: String?
}

class EventInfoStorageImpl(private val kaliumPreferences: KaliumPreferences) : EventInfoStorage {

    override var lastProcessedId: String?
        get() = kaliumPreferences.getString(LAST_PROCESSED_EVENT_ID_KEY)
        set(value) = kaliumPreferences.putString(LAST_PROCESSED_EVENT_ID_KEY, value)

    private companion object {
        const val LAST_PROCESSED_EVENT_ID_KEY = "last_processed_event_id"
    }
}
