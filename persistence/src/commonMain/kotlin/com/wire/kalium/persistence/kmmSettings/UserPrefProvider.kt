package com.wire.kalium.persistence.kmmSettings

import com.wire.kalium.persistence.client.LastRetrievedNotificationEventStorage
import com.wire.kalium.persistence.event.EventInfoStorage

expect class UserPrefProvider {
    val lastRetrievedNotificationEventStorage: LastRetrievedNotificationEventStorage
    val eventInfoStorage: EventInfoStorage
    fun clear()
}
